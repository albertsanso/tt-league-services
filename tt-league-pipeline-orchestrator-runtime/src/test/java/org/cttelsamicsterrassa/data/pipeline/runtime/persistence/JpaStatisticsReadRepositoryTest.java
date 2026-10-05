package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.port.StatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The filter edges of each statistics read; the figures themselves are computed (and tested) in the core. */
class JpaStatisticsReadRepositoryTest extends AbstractPersistenceTest {

    private static final Instant FROM = Instant.parse("2026-10-03T22:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-04T22:00:00Z");

    @Autowired
    StatisticsReadRepository reads;

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    PipelineStepRepository steps;

    @Autowired
    ImportReportRepository reports;

    @Autowired
    MatchDayRepository matchDays;

    private PipelineRun failedRun(PipelineSource source, Instant finishedAt) {
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2026-2027",
                org.cttelsamicsterrassa.data.pipeline.core.run.RunScope.fullSeason(), false,
                org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger.MANUAL, "ana", null,
                finishedAt.minusSeconds(60)));
        return runs.update(run.fail(new RunError("E", "boom"), finishedAt));
    }

    private PipelineRun succeededRun(PipelineSource source, Instant finishedAt) {
        Instant start = finishedAt.minusSeconds(60);
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2026-2027",
                org.cttelsamicsterrassa.data.pipeline.core.run.RunScope.fullSeason(), false,
                org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger.MANUAL, "ana", null, start));
        run = runs.update(run.startIngest("ing-1", start));
        run = runs.update(run.packed(start.plusSeconds(10)));
        run = runs.update(run.startImport(UUID.randomUUID(), start.plusSeconds(20)));
        return runs.update(run.succeed(finishedAt));
    }

    @Test
    void terminalRunsAreFilteredByFinishTimeAndSource() {
        PipelineRun inside = failedRun(PipelineSource.FCTT, FROM.plusSeconds(1));
        failedRun(PipelineSource.FCTT, FROM.minusSeconds(1));
        failedRun(PipelineSource.RFETM, TO);
        PipelineRun atStart = succeededRun(PipelineSource.RFETM, FROM);
        runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2026-2027",
                org.cttelsamicsterrassa.data.pipeline.core.run.RunScope.fullSeason(), false,
                org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger.MANUAL, "ana", null, FROM));

        List<RunFacts> all = reads.terminalRunsFinishedBetween(FROM, TO, Set.of());
        List<RunFacts> onlyFctt = reads.terminalRunsFinishedBetween(FROM, TO, Set.of(PipelineSource.FCTT));

        assertThat(all).extracting(RunFacts::runId).containsExactlyInAnyOrder(inside.id(), atStart.id());
        assertThat(all).filteredOn(run -> run.runId().equals(atStart.id())).first()
                .satisfies(run -> assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED));
        assertThat(onlyFctt).extracting(RunFacts::runId).containsExactly(inside.id());
    }

    @Test
    void stepsCarryTheSourceOfTheirRunAndTheirHealth() {
        PipelineRun fctt = failedRun(PipelineSource.FCTT, FROM.plusSeconds(100));
        PipelineRun rfetm = failedRun(PipelineSource.RFETM, FROM.plusSeconds(100));
        PipelineStep ingest = PipelineStep.start(UUID.randomUUID(), fctt.id(), StepKind.INGEST, 1,
                FROM.plusSeconds(10), null);
        steps.save(ingest);
        steps.save(ingest.succeed(FROM.plusSeconds(40), "SUCCEEDED", new IngestHealth(4, 2, 1)));
        PipelineStep unknown = PipelineStep.start(UUID.randomUUID(), rfetm.id(), StepKind.INGEST, 1,
                FROM.plusSeconds(10), null);
        steps.save(unknown);
        steps.save(unknown.succeed(FROM.plusSeconds(50), "NO_CHANGES"));
        PipelineStep running = PipelineStep.start(UUID.randomUUID(), rfetm.id(), StepKind.IMPORT, 1,
                FROM.plusSeconds(10), null);
        steps.save(running);
        PipelineStep outside = PipelineStep.start(UUID.randomUUID(), fctt.id(), StepKind.IMPORT, 1,
                FROM.minusSeconds(100), null);
        steps.save(outside);
        steps.save(outside.succeed(FROM.minusSeconds(1), "SUCCEEDED"));

        List<StepFacts> found = reads.stepsFinishedBetween(FROM, TO, Set.of());

        assertThat(found).hasSize(2);
        StepFacts withHealth = found.stream().filter(step -> step.source() == PipelineSource.FCTT).findFirst()
                .orElseThrow();
        assertThat(withHealth.health()).isEqualTo(new IngestHealth(4, 2, 1));
        assertThat(withHealth.status()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(found.stream().filter(step -> step.source() == PipelineSource.RFETM).findFirst().orElseThrow()
                .health()).isNull();
        assertThat(reads.stepsFinishedBetween(FROM, TO, Set.of(PipelineSource.RFETM))).hasSize(1);
    }

    @Test
    void importReportsGiveTheAmendedCountPerSourceWithinTheRange() {
        PipelineRun fctt = succeededRun(PipelineSource.FCTT, FROM.plusSeconds(500));
        PipelineRun rfetm = succeededRun(PipelineSource.RFETM, FROM.plusSeconds(500));
        reports.add(report(fctt.id(), 3, FROM.plusSeconds(600)));
        reports.add(report(rfetm.id(), 1, TO));

        List<CorrectionFacts> found = reads.importReportsReceivedBetween(FROM, TO, Set.of());

        assertThat(found).extracting(CorrectionFacts::source, CorrectionFacts::amendedPlayed)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(PipelineSource.FCTT, 3L));
        assertThat(reads.importReportsReceivedBetween(FROM, TO.plusSeconds(1), Set.of(PipelineSource.RFETM)))
                .extracting(CorrectionFacts::amendedPlayed).containsExactly(1L);
    }

    @Test
    void matchesCarryTheirMatchDayAndAreFilteredBySeasonAndSource() {
        MatchDay fctt = day(PipelineSource.FCTT, "2026-2027", "TERCERA");
        MatchDay old = day(PipelineSource.FCTT, "2025-2026", "TERCERA");
        MatchDay rfetm = day(PipelineSource.RFETM, "2026-2027", "HONOR");
        MatchTracking waiting = match(fctt, TrackedMatchStatus.AWAITING_RESULT, FROM, null);
        matchDays.apply(new MatchDayChangeSet(List.of(fctt, old, rfetm),
                List.of(waiting, match(old, TrackedMatchStatus.OVERDUE, FROM, null),
                        match(rfetm, TrackedMatchStatus.AWAITING_RESULT, FROM, null).ignore("ana", FROM)),
                Set.of(), List.of()));

        List<MatchFacts> season = reads.matchesBySeason(Set.of(), "2026-2027");
        List<MatchFacts> all = reads.matchesBySeason(Set.of(), null);

        assertThat(season).hasSize(2);
        assertThat(all).hasSize(3);
        MatchFacts fact = season.stream().filter(m -> m.matchId().equals(waiting.matchId())).findFirst()
                .orElseThrow();
        assertThat(fact.source()).isEqualTo(PipelineSource.FCTT);
        assertThat(fact.competition()).isEqualTo("TERCERA");
        assertThat(fact.season()).isEqualTo("2026-2027");
        assertThat(fact.dayState()).isEqualTo(MatchDayState.UPCOMING);
        assertThat(fact.ignored()).isFalse();
        assertThat(season.stream().filter(m -> m.source() == PipelineSource.RFETM).findFirst().orElseThrow()
                .ignored()).isTrue();
        assertThat(reads.matchesBySeason(Set.of(PipelineSource.RFETM), "2026-2027")).hasSize(1);
    }

    @Test
    void theDayPrefilterKeepsReportedOnTheDayAndStillPendingMatches() {
        MatchDay day = day(PipelineSource.FCTT, "2026-2027", "TERCERA");
        MatchTracking reportedInside = match(day, TrackedMatchStatus.REPORTED, FROM.minus(Duration.ofHours(5)),
                FROM.plusSeconds(10));
        MatchTracking reportedBefore = match(day, TrackedMatchStatus.REPORTED, FROM.minus(Duration.ofHours(9)),
                FROM.minusSeconds(10));
        MatchTracking reportedAtEnd = match(day, TrackedMatchStatus.REPORTED, FROM.minus(Duration.ofHours(5)), TO);
        MatchTracking pendingDated = match(day, TrackedMatchStatus.AWAITING_RESULT, TO.minusSeconds(1), null);
        MatchTracking pendingFuture = match(day, TrackedMatchStatus.SCHEDULED, TO, null);
        MatchTracking pendingIgnored = match(day, TrackedMatchStatus.AWAITING_RESULT, FROM, null).ignore("ana", FROM);
        matchDays.apply(new MatchDayChangeSet(List.of(day),
                List.of(reportedInside, reportedBefore, reportedAtEnd, pendingDated, pendingFuture, pendingIgnored),
                Set.of(), List.of()));

        List<MatchFacts> found = reads.matchesForDay(FROM, TO);

        assertThat(found).extracting(MatchFacts::matchId).containsExactlyInAnyOrder(
                reportedInside.matchId(), reportedAtEnd.matchId(), pendingDated.matchId());
    }

    private static ImportReport report(UUID runId, long amended, Instant receivedAt) {
        return new ImportReport(runId, UUID.randomUUID(), "SUCCEEDED", 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, amended,
                List.of(), "{}", receivedAt);
    }

    private static MatchDay day(PipelineSource source, String season, String competition) {
        return MatchDay.create(UUID.randomUUID(), new MatchDayKey(source, season, competition, 1, "1a Fase", 1),
                new MatchDayWindow(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04"), 2), T0);
    }

    private static MatchTracking match(MatchDay day, TrackedMatchStatus status, Instant played, Instant reportedAt) {
        return MatchTracking.first(UUID.randomUUID(), day.id(), status, played, "Home", "Away",
                played.minus(Duration.ofHours(1)), reportedAt, null);
    }
}
