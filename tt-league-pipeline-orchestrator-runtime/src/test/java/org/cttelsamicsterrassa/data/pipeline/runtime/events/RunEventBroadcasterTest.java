package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.RunDtoMapper;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Events;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RunEventBroadcasterTest {

    private RunEventBroadcaster broadcaster;

    private RunEventBroadcaster create(int maxSubscribers, int queueCapacity) {
        broadcaster = new RunEventBroadcaster(new RunDtoMapper(new FakeRunClock()), new ObjectMapper(),
                new Events(Duration.ofHours(1), Duration.ofMinutes(30), maxSubscribers), queueCapacity);
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
                false, RunTrigger.MANUAL, "alice", null, Instant.parse("2026-10-04T10:00:00Z"));
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
        ObjectMapper json = new ObjectMapper();

        assertThat(json.readTree(json.writeValueAsString(RunEventBroadcaster.matchDaysPayload(PipelineSource.FCTT,
                "2026-2027", null, MatchDayChangeListener.Cause.RECOMPUTED))))
                .isEqualTo(json.readTree(
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
                RunScope.fullSeason(), false, RunTrigger.RETRY, "alice", original,
                Instant.parse("2026-10-04T10:00:00Z"));

        broadcaster.runChanged(replay);

        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        com.fasterxml.jackson.databind.JsonNode payload = json.readTree(json.writeValueAsString(
                new RunDtoMapper(new FakeRunClock()).summary(replay, null)));
        assertThat(payload.get("trigger").asText()).isEqualTo("RETRY");
        assertThat(payload.get("retryOfRunId").asText()).isEqualTo(original.toString());
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }
}
