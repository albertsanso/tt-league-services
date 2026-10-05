package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small builders shared by the alert tests. */
final class AlertFixtures {

    static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    static final AlertSettings SETTINGS =
            new AlertSettings(Duration.ofHours(48), Duration.ofHours(24), Duration.ofDays(1));

    private AlertFixtures() {
    }

    static MatchDay openDay(PipelineSource source, int round, Instant openedAt) {
        MatchDayKey key = new MatchDayKey(source, "2026-2027", "TERCERA", 1, "1a Fase", round);
        MatchDayWindow window = new MatchDayWindow(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-04"), 2);
        return MatchDay.create(UUID.randomUUID(), key, window, openedAt).open(openedAt);
    }

    static MatchDay openDay(PipelineSource source, Instant openedAt) {
        return openDay(source, 1, openedAt);
    }

    static MatchDay closed(MatchDay day, CloseReason reason, Instant at) {
        return day.close(reason, reason == CloseReason.MANUAL ? "ana" : "system:tracker", at);
    }

    static MatchTracking match(MatchDay day, TrackedMatchStatus status, Instant dateTime) {
        Instant reported = status == TrackedMatchStatus.REPORTED ? dateTime : null;
        return MatchTracking.first(UUID.randomUUID(), day.id(), status, dateTime, "Home", "Away", NOW, reported,
                null);
    }

    static PipelineRun failedRun(PipelineSource source, Instant finishedAt, String code) {
        return queued(source, finishedAt.minusSeconds(60)).fail(new RunError(code, "secret message " + code),
                finishedAt);
    }

    static PipelineRun succeededRun(PipelineSource source, Instant finishedAt) {
        return queued(source, finishedAt.minusSeconds(60))
                .startIngest("ing", finishedAt.minusSeconds(50))
                .packed(finishedAt.minusSeconds(40))
                .startImport(UUID.randomUUID(), finishedAt.minusSeconds(30))
                .succeed(finishedAt);
    }

    static PipelineRun partialRun(PipelineSource source, Instant finishedAt) {
        return queued(source, finishedAt.minusSeconds(60))
                .startIngest("ing", finishedAt.minusSeconds(50))
                .packed(finishedAt.minusSeconds(40))
                .startImport(UUID.randomUUID(), finishedAt.minusSeconds(30))
                .partial(finishedAt);
    }

    static PipelineRun noChangesRun(PipelineSource source, Instant finishedAt) {
        return queued(source, finishedAt.minusSeconds(60))
                .startIngest("ing", finishedAt.minusSeconds(50))
                .noChanges(finishedAt);
    }

    private static PipelineRun queued(PipelineSource source, Instant createdAt) {
        return PipelineRun.queue(UUID.randomUUID(), source, "2026-2027", RunScope.fullSeason(), false,
                RunTrigger.SCHEDULED, "system:scheduler", null, createdAt);
    }

    static AlertFacts empty() {
        return facts(List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of());
    }

    static AlertFacts facts(
            List<MatchDay> openDays,
            List<MatchDay> closedDays,
            List<MatchTracking> openMatches,
            List<MatchDay> activeAlertDays,
            Map<PipelineSource, List<PipelineRun>> runs,
            Map<PipelineSource, Instant> success) {
        return new AlertFacts(openDays, closedDays, openMatches, activeAlertDays, runs, success);
    }
}
