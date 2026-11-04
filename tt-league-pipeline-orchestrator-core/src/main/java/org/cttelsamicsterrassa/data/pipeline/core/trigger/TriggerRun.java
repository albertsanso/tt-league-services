package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher.LaunchRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** The one path that creates new runs for manual and scheduled triggers. */
public final class TriggerRun {

    public static final String ACTIVE_RUN = "ACTIVE_RUN";
    public static final String PENDING_EXISTS = "PENDING_EXISTS";

    private static final System.Logger LOG = System.getLogger(TriggerRun.class.getName());

    /** What a caller asks for. {@code RETRY} runs are not created here. */
    public record Command(
            List<PipelineSource> sources,
            String season,
            ScopeType scopeType,
            List<ScopeFilter> filters,
            boolean force,
            RunTrigger trigger,
            String requestedBy,
            ConflictMode conflictMode) {

        public Command {
            Objects.requireNonNull(sources, "sources is required");
            sources = List.copyOf(sources);
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("sources must not be empty");
            }
            if (sources.stream().distinct().count() != sources.size()) {
                throw new IllegalArgumentException("sources must be distinct");
            }
            Objects.requireNonNull(filters, "filters is required");
            filters = List.copyOf(filters);
            TriggerRules.validate(season, scopeType, filters, requestedBy);
            if (scopeType == ScopeType.GROUP && sources.size() != 1) {
                throw new IllegalArgumentException("scopeType GROUP requires exactly one source");
            }
            Objects.requireNonNull(trigger, "trigger is required");
            if (trigger == RunTrigger.RETRY) {
                throw new IllegalArgumentException("trigger must be MANUAL or SCHEDULED");
            }
            Objects.requireNonNull(conflictMode, "conflictMode is required");
        }
    }

    /** Result for one source; sources are processed independently. */
    public sealed interface Outcome {

        PipelineSource source();

        record Created(PipelineRun run) implements Outcome {

            @Override
            public PipelineSource source() {
                return run.source();
            }
        }

        record Queued(PendingTrigger pending, UUID activeRunId) implements Outcome {

            @Override
            public PipelineSource source() {
                return pending.source();
            }
        }

        /** Codes {@code ACTIVE_RUN} and {@code PENDING_EXISTS}; {@code activeRunId} is null when it just ended. */
        record Rejected(PipelineSource source, String code, String message, UUID activeRunId) implements Outcome {
        }

        record Unavailable(PipelineSource source, String code, String message) implements Outcome {
        }
    }

    private final PipelineRunRepository runs;
    private final PendingTriggerRepository pendingTriggers;
    private final OpenMatchDayScopeResolver openMatchDays;
    private final RunLauncher launcher;
    private final PendingTriggerEvents events;
    private final RunClock clock;

    public TriggerRun(
            PipelineRunRepository runs,
            PendingTriggerRepository pendingTriggers,
            OpenMatchDayScopeResolver openMatchDays,
            RunLauncher launcher,
            PendingTriggerEvents events,
            RunClock clock) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.pendingTriggers = Objects.requireNonNull(pendingTriggers, "pendingTriggers is required");
        this.openMatchDays = Objects.requireNonNull(openMatchDays, "openMatchDays is required");
        this.launcher = Objects.requireNonNull(launcher, "launcher is required");
        this.events = Objects.requireNonNull(events, "events is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public List<Outcome> trigger(Command command) {
        List<Outcome> outcomes = new ArrayList<>();
        for (PipelineSource source : PipelineSource.values()) {
            if (command.sources().contains(source)) {
                outcomes.add(triggerSource(command, source));
            }
        }
        return outcomes;
    }

    private Outcome triggerSource(Command command, PipelineSource source) {
        RunScope scope;
        try {
            scope = resolve(command.scopeType(), command.filters(), source, command.season());
        } catch (ScopeUnavailableException e) {
            return new Outcome.Unavailable(source, e.code(), e.getMessage());
        }
        try {
            return new Outcome.Created(launcher.launch(new LaunchRequest(source, command.season(), scope,
                    command.force(), command.trigger(), command.requestedBy(), null)));
        } catch (ActiveRunConflictException conflict) {
            return onConflict(command, source);
        }
    }

    private Outcome onConflict(Command command, PipelineSource source) {
        Optional<PipelineRun> active = runs.findActiveBySource(source);
        UUID activeId = active.map(PipelineRun::id).orElse(null);
        if (command.conflictMode() == ConflictMode.REJECT) {
            return new Outcome.Rejected(source, ACTIVE_RUN, activeMessage(source, active), activeId);
        }
        PendingTrigger pending = new PendingTrigger(source, command.season(), command.scopeType(), command.filters(),
                command.force(), command.requestedBy(), clock.now());
        try {
            pendingTriggers.add(pending);
        } catch (PendingTriggerExistsException exists) {
            return new Outcome.Rejected(source, PENDING_EXISTS,
                    "Source " + source + " already has a pending trigger waiting for its active run", activeId);
        }
        notifyEvents(listener -> listener.queued(pending));
        if (runs.findActiveBySource(source).isEmpty()) {
            launchPending(source);
        }
        return new Outcome.Queued(pending, activeId);
    }

    /**
     * Launches the source's pending trigger, if any, as a MANUAL run. A conflict (another trigger won the race) puts
     * it back and returns empty; an unavailable scope drops it. Other failures propagate.
     */
    public Optional<PipelineRun> launchPending(PipelineSource source) {
        Optional<PendingTrigger> taken = pendingTriggers.take(source);
        if (taken.isEmpty()) {
            return Optional.empty();
        }
        PendingTrigger pending = taken.get();
        RunScope scope;
        try {
            scope = resolve(pending.scopeType(), pending.filters(), source, pending.season());
        } catch (ScopeUnavailableException e) {
            LOG.log(System.Logger.Level.WARNING, "Dropping pending trigger for {0} requested by {1}: {2}", source,
                    pending.requestedBy(), e.code());
            notifyEvents(listener -> listener.dropped(pending, e.code()));
            return Optional.empty();
        }
        try {
            PipelineRun run = launcher.launch(new LaunchRequest(source, pending.season(), scope, pending.force(),
                    RunTrigger.MANUAL, pending.requestedBy(), null));
            notifyEvents(listener -> listener.launched(pending, run));
            return Optional.of(run);
        } catch (ActiveRunConflictException conflict) {
            restore(pending);
            return Optional.empty();
        } catch (RuntimeException e) {
            notifyEvents(listener -> listener.dropped(pending, "LAUNCH_FAILED"));
            throw e;
        }
    }

    private void restore(PendingTrigger pending) {
        try {
            pendingTriggers.add(pending);
        } catch (PendingTriggerExistsException newer) {
            LOG.log(System.Logger.Level.WARNING,
                    "Dropping pending trigger for {0} requested by {1}: superseded by a newer one",
                    pending.source(), pending.requestedBy());
            notifyEvents(listener -> listener.dropped(pending, "SUPERSEDED"));
        }
    }

    /** Launches every stored pending trigger whose source has no active run; returns the number launched. */
    public int drainIdle() {
        int launched = 0;
        for (PendingTrigger pending : pendingTriggers.findAll()) {
            if (runs.findActiveBySource(pending.source()).isEmpty()
                    && launchPending(pending.source()).isPresent()) {
                launched++;
            }
        }
        return launched;
    }

    private RunScope resolve(ScopeType type, List<ScopeFilter> filters, PipelineSource source, String season) {
        return switch (type) {
            case FULL_SEASON -> RunScope.fullSeason();
            case GROUP -> new RunScope(filters);
            case OPEN_MATCH_DAYS -> {
                RunScope scope = openMatchDays.resolve(source, season);
                if (scope.isFullSeason()) {
                    throw new IllegalStateException("OpenMatchDayScopeResolver returned a scope without filters");
                }
                yield scope;
            }
        };
    }

    private static String activeMessage(PipelineSource source, Optional<PipelineRun> active) {
        return active
                .map(run -> "Source " + source + " already has an active run " + run.id() + " (" + run.status() + ")")
                .orElse("Source " + source + " already has an active run");
    }

    /** Listeners are side channels: a failure is logged and never reaches the caller. */
    private void notifyEvents(Consumer<PendingTriggerEvents> call) {
        try {
            call.accept(events);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Pending-trigger listener failed: {0}: {1}",
                    e.getClass().getName(), e.getMessage());
        }
    }
}
