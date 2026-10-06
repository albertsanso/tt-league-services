package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Events;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RunEventBroadcasterTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    /** An emitter that records the text of everything sent to the subscriber. */
    private static final class RecordingEmitter extends SseEmitter {

        final List<String> sent = Collections.synchronizedList(new ArrayList<>());

        RecordingEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            StringBuilder text = new StringBuilder();
            builder.build().forEach(part -> text.append(part.getData()));
            sent.add(text.toString());
        }
    }

    private RunEventBroadcaster broadcaster;
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryRunUnitRepository units = new InMemoryRunUnitRepository(runs);
    private final InMemoryImportReportRepository reports = new InMemoryImportReportRepository();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private RecordingEmitter emitter;

    private RunEventBroadcaster create(int maxSubscribers, int queueCapacity) {
        broadcaster = new RunEventBroadcaster(new RunDtoMapper(new FakeRunClock()), units, reports, json,
                new Events(Duration.ofHours(1), Duration.ofMinutes(30), maxSubscribers), queueCapacity,
                timeout -> {
                    emitter = new RecordingEmitter(timeout);
                    return emitter;
                });
        return broadcaster;
    }

    @AfterEach
    void stop() {
        if (broadcaster != null) {
            broadcaster.shutdown();
        }
    }

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "alice", null, T0);
    }

    private void awaitEvents(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (emitter.sent.size() < count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("expected " + count + " events but got " + emitter.sent);
            }
            Thread.sleep(10);
        }
    }

    /** The JSON payload of the {@code data:} line of an event. */
    private JsonNode payload(String event) throws Exception {
        for (String line : event.split("\n")) {
            if (line.startsWith("data:")) {
                return json.readTree(line.substring("data:".length()));
            }
        }
        throw new AssertionError("no data in " + event);
    }

    @Test
    void subscribersAreCappedAndCounted() {
        create(2, 10);

        broadcaster.subscribe();
        broadcaster.subscribe();

        assertThat(broadcaster.subscriberCount()).isEqualTo(2);
        assertThatThrownBy(broadcaster::subscribe).isInstanceOf(RunEventBroadcaster.TooManySubscribersException.class);
    }

    @Test
    void publishingNeverBlocksOrThrowsEvenWhenTheQueueIsFull() {
        create(5, 1);
        broadcaster.subscribe();

        for (int i = 0; i < 2000; i++) {
            broadcaster.runChanged(run());
        }

        assertThat(broadcaster.droppedEvents()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shutdownCompletesAndRemovesSubscribers() {
        create(5, 10);
        broadcaster.subscribe();

        broadcaster.shutdown();

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    void matchDayChangesCarrySourceSeasonOptionalIdAndCause() throws Exception {
        UUID id = UUID.randomUUID();
        ObjectMapper plain = new ObjectMapper();

        assertThat(plain.readTree(plain.writeValueAsString(RunEventBroadcaster.matchDaysPayload(PipelineSource.FCTT,
                "2026-2027", null, MatchDayChangeListener.Cause.RECOMPUTED))))
                .isEqualTo(plain.readTree(
                        "{\"source\":\"FCTT\",\"season\":\"2026-2027\",\"matchDayId\":null,"
                                + "\"cause\":\"RECOMPUTED\"}"));
        assertThat(RunEventBroadcaster.matchDaysPayload(PipelineSource.RFETM, "2026-2027", id,
                MatchDayChangeListener.Cause.ACTION))
                .containsEntry("matchDayId", id.toString()).containsEntry("cause", "ACTION");
    }

    @Test
    void publishingMatchDayChangesNeverThrows() {
        create(5, 10);
        broadcaster.subscribe();

        broadcaster.matchDaysChanged(PipelineSource.FCTT, "2026-2027", null, MatchDayChangeListener.Cause.RECOMPUTED);
        broadcaster.matchDaysChanged(PipelineSource.FCTT, "2026-2027", UUID.randomUUID(),
                MatchDayChangeListener.Cause.ACTION);

        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }

    @Test
    void aReplayRunIsPublishedLikeAnyOtherRunAndCarriesItsTriggerAndOrigin() throws Exception {
        create(5, 10);
        broadcaster.subscribe();
        UUID original = UUID.randomUUID();
        PipelineRun replay = PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.RETRY, "alice", original, T0);

        broadcaster.runChanged(replay);
        awaitEvents(2);

        JsonNode payload = payload(emitter.sent.get(1));
        assertThat(emitter.sent.get(1)).contains("event:run");
        assertThat(payload.get("trigger").asText()).isEqualTo("RETRY");
        assertThat(payload.get("retryOfRunId").asText()).isEqualTo(original.toString());
        assertThat(payload.get("retryOfUnitId").isNull()).isTrue();
    }

    @Test
    void aRunEventCarriesTheSummaryOfItsStoredUnits() throws Exception {
        create(5, 10);
        broadcaster.subscribe();
        ScopeFilter g1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
        ScopeFilter g2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3));
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                new RunScope(List.of(g1, g2)), false, RunTrigger.MANUAL, "alice", null, T0));
        List<RunUnit> planned = units.addAll(UnitPlanner.plan(run.id(), run.scope()));
        units.update(planned.get(0).startIngest("ing1", T0));

        broadcaster.runChanged(run);
        awaitEvents(2);

        JsonNode payload = payload(emitter.sent.get(1));
        assertThat(payload.get("units")).hasSize(2);
        assertThat(payload.get("units").get(0).get("status").asText()).isEqualTo("RUNNING_INGEST");
        assertThat(payload.get("units").get(1).get("status").asText()).isEqualTo("PENDING");
        assertThat(payload.get("units").get(0).get("unitKey").asText()).isEqualTo(UnitKey.of(g1));
        assertThat(payload.get("units").get(0).get("counters").isNull()).isTrue();
        assertThat(payload.get("currentUnitId").asText()).isEqualTo(planned.get(0).id().toString());
    }

    @Test
    void aUnitEventCarriesTheUnitWithItsProgress() throws Exception {
        create(5, 10);
        broadcaster.subscribe();
        UUID runId = UUID.randomUUID();
        RunUnit unit = RunUnit.plan(UUID.randomUUID(), runId, 0, UnitKey.SEASON, "Full season", RunScope.fullSeason())
                .startIngest("ing1", T0)
                .withProgress(new UnitProgress(StepKind.INGEST, "DOWNLOAD", 4, 8L, "league four", T0.plusSeconds(5)));

        broadcaster.unitChanged(unit);
        awaitEvents(2);

        assertThat(emitter.sent.get(1)).contains("event:unit");
        JsonNode payload = payload(emitter.sent.get(1));
        assertThat(payload.get("runId").asText()).isEqualTo(runId.toString());
        assertThat(payload.get("id").asText()).isEqualTo(unit.id().toString());
        assertThat(payload.get("status").asText()).isEqualTo("RUNNING_INGEST");
        assertThat(payload.get("progress").get("step").asText()).isEqualTo("INGEST");
        assertThat(payload.get("progress").get("stage").asText()).isEqualTo("DOWNLOAD");
        assertThat(payload.get("progress").get("itemsProcessed").asInt()).isEqualTo(4);
        assertThat(payload.get("progress").get("itemsTotal").asInt()).isEqualTo(8);
        assertThat(payload.get("progress").get("percent").asInt()).isEqualTo(50);
        assertThat(payload.get("progress").get("currentItem").asText()).isEqualTo("league four");
    }

    @Test
    void aTerminalUnitEventCarriesTheCountersOfItsImportReportAndARunningOneDoesNot() throws Exception {
        create(5, 10);
        broadcaster.subscribe();
        UUID runId = UUID.randomUUID();
        RunUnit running = RunUnit.plan(UUID.randomUUID(), runId, 0, UnitKey.SEASON, "Full season", RunScope.fullSeason())
                .startIngest("ing1", T0);
        RunUnit finished = running.packed(T0.plusSeconds(1)).startImport(UUID.randomUUID(), T0.plusSeconds(2))
                .succeed(T0.plusSeconds(3));
        reports.add(new ImportReport(runId, finished.id(), finished.importJobId(), "SUCCEEDED", 11, 22, 3, 4, 5, 6, 7, 8,
                9, 10, 12, List.of(), "{}", T0.plusSeconds(3)));

        broadcaster.unitChanged(running);
        broadcaster.unitChanged(finished);
        awaitEvents(3);

        assertThat(payload(emitter.sent.get(1)).get("counters").isNull()).isTrue();
        JsonNode counters = payload(emitter.sent.get(2)).get("counters");
        assertThat(counters.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(counters.get("filesSeen").asInt()).isEqualTo(11);
        assertThat(counters.get("itemsPersisted").asInt()).isEqualTo(22);
    }

    @Test
    void aTerminalUnitWithoutAReportHasNoCounters() throws Exception {
        create(5, 10);
        broadcaster.subscribe();
        RunUnit noChanges = RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, UnitKey.SEASON, "Full season",
                RunScope.fullSeason()).startIngest("ing1", T0).noChanges(T0.plusSeconds(1));

        broadcaster.unitChanged(noChanges);
        awaitEvents(2);

        assertThat(payload(emitter.sent.get(1)).get("counters").isNull()).isTrue();
    }

    @Test
    void nothingIsReadOrBuiltWhenNobodyListens() {
        InMemoryRunUnitRepository failing = new InMemoryRunUnitRepository(new InMemoryPipelineRunRepository()) {
            @Override
            public synchronized List<RunUnit> findByRunId(UUID runId) {
                throw new AssertionError("the units must not be read without subscribers");
            }
        };
        RunEventBroadcaster idle = new RunEventBroadcaster(new RunDtoMapper(new FakeRunClock()), failing, reports, json,
                new Events(Duration.ofHours(1), Duration.ofMinutes(30), 5), 10);
        try {
            idle.runChanged(run());
            idle.unitChanged(RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, UnitKey.SEASON, "Full season",
                    RunScope.fullSeason()));
            assertThat(idle.droppedEvents()).isZero();
        } finally {
            idle.shutdown();
        }
    }
}
