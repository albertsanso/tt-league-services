package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Publishes run, step, pending-trigger and match-day changes to the Server-Sent Events subscribers. Callers (executor threads)
 * only build the payload and enqueue it: sending happens on a private single-thread pool with a bounded queue, so a
 * slow client never blocks a run. When the queue is full the event is dropped and the drop is logged at most once a
 * minute. The pools are owned here and are deliberately not {@code Executor} beans.
 */
public final class RunEventBroadcaster implements RunObserver, PendingTriggerEvents, MatchDayChangeListener {

    static final int QUEUE_CAPACITY = 1000;
    static final long RECONNECT_MILLIS = 5000;
    private static final long DROP_LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final Logger LOG = LoggerFactory.getLogger(RunEventBroadcaster.class);

    /** Thrown by {@link #subscribe()} when the subscriber cap is reached. */
    public static final class TooManySubscribersException extends RuntimeException {

        TooManySubscribersException(int max) {
            super("The event stream is limited to " + max + " subscribers");
        }
    }

    private final RunDtoMapper mapper;
    private final ObjectMapper json;
    private final PipelineOrchestratorProperties.Events settings;
    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final ThreadPoolExecutor sender;
    private final ScheduledExecutorService heartbeat;
    private final AtomicLong dropped = new AtomicLong();
    private long lastDropLogNanos = System.nanoTime() - DROP_LOG_INTERVAL_NANOS;

    public RunEventBroadcaster(
            RunDtoMapper mapper, ObjectMapper json, PipelineOrchestratorProperties.Events settings) {
        this(mapper, json, settings, QUEUE_CAPACITY);
    }

    RunEventBroadcaster(
            RunDtoMapper mapper, ObjectMapper json, PipelineOrchestratorProperties.Events settings,
            int queueCapacity) {
        this.mapper = mapper;
        this.json = json;
        this.settings = settings;
        ThreadFactory sendThreads = daemon("run-events-sender");
        this.sender = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), sendThreads);
        ThreadFactory heartbeatThreads = daemon("run-events-heartbeat");
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, heartbeatThreads);
        scheduler.setRemoveOnCancelPolicy(true);
        this.heartbeat = scheduler;
        long period = settings.heartbeatInterval().toMillis();
        this.heartbeat.scheduleAtFixedRate(this::keepAlive, period, period, TimeUnit.MILLISECONDS);
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Registers a subscriber; its first events are {@code retry: 5000} and {@code ready}. */
    public synchronized SseEmitter subscribe() {
        if (emitters.size() >= settings.maxSubscribers()) {
            throw new TooManySubscribersException(settings.maxSubscribers());
        }
        SseEmitter emitter = new SseEmitter(settings.emitterTimeout().toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS).name("ready").data("{}",
                    MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            emitter.complete();
            return emitter;
        }
        emitters.add(emitter);
        return emitter;
    }

    public int subscriberCount() {
        return emitters.size();
    }

    public long droppedEvents() {
        return dropped.get();
    }

    @Override
    public void runChanged(PipelineRun run) {
        publish("run", mapper.summary(run, null));
    }

    @Override
    public void stepChanged(PipelineStep step) {
        publish("step", mapper.step(step));
    }

    @Override
    public void queued(PendingTrigger trigger) {
        publish("pending-trigger", pending(trigger, "QUEUED", null, null));
    }

    @Override
    public void launched(PendingTrigger trigger, PipelineRun run) {
        publish("pending-trigger", pending(trigger, "LAUNCHED", run.id().toString(), null));
    }

    @Override
    public void dropped(PendingTrigger trigger, String code) {
        publish("pending-trigger", pending(trigger, "DROPPED", null, code));
    }

    @Override
    public void matchDaysChanged(PipelineSource source, String season, UUID matchDayId, Cause cause) {
        publish("match-days", matchDaysPayload(source, season, matchDayId, cause));
    }

    static Map<String, Object> matchDaysPayload(
            PipelineSource source, String season, UUID matchDayId, Cause cause) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source.name());
        payload.put("season", season);
        payload.put("matchDayId", matchDayId == null ? null : matchDayId.toString());
        payload.put("cause", cause.name());
        return payload;
    }

    private static Map<String, Object> pending(PendingTrigger trigger, String state, String runId, String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", trigger.source().name());
        payload.put("state", state);
        payload.put("requestedBy", trigger.requestedBy());
        if (runId != null) {
            payload.put("runId", runId);
        }
        if (code != null) {
            payload.put("code", code);
        }
        return payload;
    }

    private void publish(String name, Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        String data;
        try {
            data = json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.warn("event {} not published: cannot serialize payload ({})", name, e.getClass().getSimpleName());
            return;
        }
        enqueue(() -> send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON)));
    }

    private void keepAlive() {
        if (!emitters.isEmpty()) {
            enqueue(() -> send(SseEmitter.event().comment("keep-alive")));
        }
    }

    private void enqueue(Runnable task) {
        try {
            sender.execute(task);
        } catch (RejectedExecutionException e) {
            dropped.incrementAndGet();
            logDrop();
        }
    }

    private synchronized void logDrop() {
        long now = System.nanoTime();
        if (now - lastDropLogNanos >= DROP_LOG_INTERVAL_NANOS) {
            lastDropLogNanos = now;
            LOG.warn("event queue is full; {} event(s) dropped so far", dropped.get());
        }
    }

    private void send(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (IllegalStateException alreadyCompleted) {
                    // the connection is gone already; nothing left to release
                }
            }
        }
    }

    /** Completes every subscriber and stops both pools. */
    public void shutdown() {
        heartbeat.shutdownNow();
        sender.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (IllegalStateException alreadyCompleted) {
                // the connection is gone already
            }
        }
        emitters.clear();
    }
}
