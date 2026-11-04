package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayEligibility;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.UnitRetryEligibility;
import org.junit.jupiter.api.Test;

class RunDtoMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final ScopeFilter G1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final ScopeFilter G2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3, 4));
    private static final String SHA = "a".repeat(64);

    private final RunDtoMapper mapper = new RunDtoMapper(new FakeRunClock(T0.plusSeconds(60)));

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "alice", null, T0);
    }

    private static PipelineRun scopedRun(RunTrigger trigger, UUID retryOfRun, UUID retryOfUnit) {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2026-2027",
                new RunScope(List.of(G1, G2)), false, trigger, "alice", retryOfRun, retryOfUnit, T0);
    }

    private static RunUnit unit(PipelineRun run, int ordinal, ScopeFilter filter) {
        return RunUnit.plan(UUID.randomUUID(), run.id(), ordinal, UnitKey.of(filter), "SENIOR " + filter.group(),
                new RunScope(List.of(filter)));
    }

    private static PipelineStep step(PipelineRun run, RunUnit unit, StepKind kind, int attempt) {
        return PipelineStep.start(UUID.randomUUID(), run.id(), unit.id(), kind, attempt, T0, null);
    }

    private static ImportReport report(PipelineRun run, RunUnit unit, String status, long files, long persisted,
            long amended, String... issues) {
        return new ImportReport(run.id(), unit.id(), UUID.randomUUID(), status, files, persisted, 3, 4, 5, 6, 7, 8, 9,
                10, amended, List.of(issues), "{}", T0.plusSeconds(files));
    }

    // ------------------------------------------------------------------ steps

    @Test
    void anIngestStepWithHealthExposesIt() {
        PipelineRun run = run();
        RunUnit unit = unit(run, 0, G1);
        PipelineStep started = PipelineStep.start(UUID.randomUUID(), run.id(), unit.id(), StepKind.INGEST, 1, T0,
                "ing1");

        StepDto dto = mapper.step(started.succeed(T0.plusSeconds(5), "SUCCEEDED", new IngestHealth(3, 2, 1)));

        assertThat(dto.health()).isEqualTo(new StepDto.Health(3, 2, 1));
        assertThat(dto.unitId()).isEqualTo(unit.id());
        assertThat(dto.runId()).isEqualTo(run.id());
    }

    @Test
    void stepsWithoutHealthExposeNull() {
        PipelineRun run = run();
        RunUnit unit = unit(run, 0, G1);
        PipelineStep ingest = step(run, unit, StepKind.INGEST, 1);
        PipelineStep importing = step(run, unit, StepKind.IMPORT, 1);

        assertThat(mapper.step(ingest).health()).isNull();
        assertThat(mapper.step(ingest.succeed(T0.plusSeconds(5), "NO_CHANGES")).health()).isNull();
        assertThat(mapper.step(importing.succeed(T0.plusSeconds(5), "SUCCEEDED")).health()).isNull();
    }

    // ------------------------------------------------------------ run summary

    @Test
    void aOneUnitRunTakesItsIngestRunAndImportJobFromTheUnit() {
        PipelineRun run = run().start(T0);
        UUID job = UUID.randomUUID();
        RunUnit unit = unit(run, 0, G1).startIngest("ing1", T0).packed(T0).startImport(job, T0);

        RunSummaryDto summary = mapper.summary(run, null, List.of(unit));

        assertThat(summary.ingestRunId()).isEqualTo("ing1");
        assertThat(summary.importJobId()).isEqualTo(job);
        assertThat(summary.currentUnitId()).isEqualTo(unit.id());
        assertThat(summary.units()).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(unit.id());
            assertThat(dto.runId()).isEqualTo(run.id());
            assertThat(dto.status()).isEqualTo("IMPORTING");
            assertThat(dto.counters()).isNull();
        });
    }

    @Test
    void aRunWithSeveralUnitsHasNoSingleIngestRunOrImportJob() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0);
        RunUnit first = unit(run, 0, G1).startIngest("ing1", T0).noChanges(T0.plusSeconds(1));
        RunUnit second = unit(run, 1, G2).startIngest("ing2", T0.plusSeconds(1));

        RunSummaryDto summary = mapper.summary(run, null, List.of(first, second));

        assertThat(summary.ingestRunId()).isNull();
        assertThat(summary.importJobId()).isNull();
        assertThat(summary.currentUnitId()).isEqualTo(second.id());
        assertThat(summary.units()).extracting(RunUnitDto::ordinal).containsExactly(0, 1);
    }

    @Test
    void withoutUnitsTheSummaryLeavesThemOutAndNoUnitIsCurrent() {
        PipelineRun run = run();

        RunSummaryDto summary = mapper.summary(run, null);

        assertThat(summary.units()).isNull();
        assertThat(summary.currentUnitId()).isNull();
        assertThat(summary.ingestRunId()).isNull();
        assertThat(mapper.summary(run, List.of(), List.of()).units()).isEmpty();
    }

    @Test
    void aPendingUnitIsNotTheCurrentOne() {
        PipelineRun run = run().start(T0);

        assertThat(mapper.summary(run, null, List.of(unit(run, 0, G1))).currentUnitId()).isNull();
    }

    @Test
    void aUnitRetryRunExposesItsOriginalRunAndUnit() {
        UUID original = UUID.randomUUID();
        UUID originalUnit = UUID.randomUUID();
        PipelineRun retry = scopedRun(RunTrigger.UNIT_RETRY, original, originalUnit);

        RunSummaryDto summary = mapper.summary(retry, null);

        assertThat(summary.trigger()).isEqualTo("UNIT_RETRY");
        assertThat(summary.retryOfRunId()).isEqualTo(original);
        assertThat(summary.retryOfUnitId()).isEqualTo(originalUnit);
    }

    @Test
    void importJobReusedComesFromTheImportStepThatSubmittedTheUnitsJob() {
        UUID job = UUID.randomUUID();
        PipelineRun base = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027",
                RunScope.fullSeason(), false, RunTrigger.RETRY, "alice", UUID.randomUUID(), T0);
        PipelineRun running = base.start(T0.plusSeconds(1));
        RunUnit importing = RunUnit.plan(UUID.randomUUID(), base.id(), 0, UnitKey.SEASON, "Full season",
                RunScope.fullSeason()).startReplay(T0.plusSeconds(1)).startImport(job, T0.plusSeconds(2));
        PipelineStep failedAttempt = step(base, importing, StepKind.IMPORT, 1)
                .fail(T0.plusSeconds(1), null, new RunError("X", "x"), true);
        PipelineStep reused = PipelineStep.start(UUID.randomUUID(), base.id(), importing.id(), StepKind.IMPORT, 2,
                T0.plusSeconds(1), null).withImportJob(job, true);

        assertThat(mapper.summary(running, List.of(failedAttempt, reused), List.of(importing)).importJobReused())
                .isTrue();
        assertThat(mapper.summary(running, null, List.of(importing)).importJobReused()).isNull();
        assertThat(mapper.summary(running, List.of(reused), null).importJobReused()).isNull();
        assertThat(mapper.step(reused).importJobReused()).isTrue();
    }

    @Test
    void theStepSummaryShowsTheLatestAttemptOfEachKindAcrossUnits() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0);
        RunUnit first = unit(run, 0, G1);
        RunUnit second = unit(run, 1, G2);
        PipelineStep firstIngest = PipelineStep.start(UUID.randomUUID(), run.id(), first.id(), StepKind.INGEST, 1, T0,
                null).succeed(T0.plusSeconds(1), "NO_CHANGES");
        PipelineStep secondIngest = PipelineStep.start(UUID.randomUUID(), run.id(), second.id(), StepKind.INGEST, 1,
                T0.plusSeconds(5), null);

        RunSummaryDto summary = mapper.summary(run, List.of(firstIngest, secondIngest), null);

        assertThat(summary.steps()).singleElement().satisfies(latest -> {
            assertThat(latest.kind()).isEqualTo("INGEST");
            assertThat(latest.status()).isEqualTo("RUNNING");
        });
    }

    // ------------------------------------------------------------------- units

    @Test
    void aUnitExposesItsScopeStatusTimingsErrorAndProgress() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0);
        RunUnit running = unit(run, 1, G2).startIngest("ing1", T0)
                .withProgress(new UnitProgress(StepKind.INGEST, "DOWNLOAD", 3, 12L, "acta-3", T0.plusSeconds(20)));

        RunUnitDto dto = mapper.unit(running);

        assertThat(dto.filters()).containsExactly(ScopeFilterDto.from(G2));
        assertThat(dto.unitKey()).isEqualTo(UnitKey.of(G2));
        assertThat(dto.label()).isEqualTo("SENIOR G2");
        assertThat(dto.ordinal()).isEqualTo(1);
        assertThat(dto.startedAt()).isEqualTo(T0);
        assertThat(dto.durationMs()).isEqualTo(60_000L);
        assertThat(dto.progress()).isEqualTo(new UnitProgressDto("INGEST", "DOWNLOAD", 3, 12L, 25, "acta-3",
                T0.plusSeconds(20)));
        assertThat(dto.error()).isNull();

        RunUnitDto failed = mapper.unit(running.fail(new RunError("INGEST_FAILED", "boom"), T0.plusSeconds(30)));
        assertThat(failed.error()).isEqualTo(new ErrorDto("INGEST_FAILED", "boom"));
        assertThat(failed.durationMs()).isEqualTo(30_000L);
        assertThat(failed.progress()).isNull();
    }

    @Test
    void theProgressPercentageIsOnlyKnownWithAPositiveTotalAndNeverExceeds100() {
        PipelineRun run = run().start(T0);
        RunUnit base = unit(run, 0, G1).startIngest("i", T0);

        assertThat(mapper.unit(base.withProgress(new UnitProgress(StepKind.INGEST, null, 0, null, null, T0)))
                .progress().percent()).isNull();
        assertThat(mapper.unit(base.withProgress(new UnitProgress(StepKind.INGEST, null, 0, 0L, null, T0)))
                .progress().percent()).isNull();
        assertThat(mapper.unit(base.withProgress(new UnitProgress(StepKind.INGEST, null, 7, 7L, null, T0)))
                .progress().percent()).isEqualTo(100);
        assertThat(mapper.unit(base.withProgress(new UnitProgress(StepKind.INGEST, null, 1, 3L, null, T0)))
                .progress().percent()).isEqualTo(33);
        assertThat(mapper.unit(base.withProgress(new UnitProgress(StepKind.FETCH_PACKAGE, null, 0, null, null, T0)))
                .progress().itemsTotal()).isNull();
    }

    @Test
    void aSkippedUnitHasNoDuration() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0);
        RunUnit skipped = unit(run, 1, G2).skip(new RunError("UNIT_SKIPPED", "skipped after X on unit Y"),
                T0.plusSeconds(3));

        assertThat(mapper.unit(skipped).durationMs()).isNull();
        assertThat(mapper.unit(skipped).error().code()).isEqualTo("UNIT_SKIPPED");
    }

    // ------------------------------------------------------------------ detail

    private RunDetailDto detail(PipelineRun run, List<RunUnit> units, List<PipelineStep> steps,
            List<RunArtifact> artifacts, List<ImportReport> reports) {
        return mapper.detail(run, units, steps, artifacts, reports, ReplayEligibility.yes(), Map.of(), Map.of());
    }

    @Test
    void theImportReportExposesTheAmendedCountOfASingleUnit() {
        PipelineRun run = run();
        RunUnit unit = unit(run, 0, G1);

        RunDetailDto detail = detail(run, List.of(unit), List.of(), List.of(),
                List.of(report(run, unit, "SUCCEEDED", 1, 2, 11)));

        assertThat(detail.importReport().amendedPlayed()).isEqualTo(11);
        assertThat(detail.importReport().unresolvedPendingFixtures()).isEqualTo(10);
        assertThat(detail.importReport().status()).isEqualTo("SUCCEEDED");
        assertThat(detail.units()).singleElement()
                .satisfies(u -> assertThat(u.unit().counters().amendedPlayed()).isEqualTo(11));
    }

    @Test
    void theRunImportReportSumsTheUnitReportsAndKeepsTheLatestReceivedTime() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null);
        RunUnit first = unit(run, 0, G1);
        RunUnit second = unit(run, 1, G2);
        ImportReport one = report(run, first, "SUCCEEDED", 10, 20, 1);
        ImportReport two = report(run, second, "PARTIAL", 5, 6, 2);

        ImportReportDto sum = detail(run, List.of(first, second), List.of(), List.of(), List.of(one, two))
                .importReport();

        assertThat(sum.status()).isEqualTo("PARTIAL");
        assertThat(sum.filesSeen()).isEqualTo(15);
        assertThat(sum.itemsPersisted()).isEqualTo(26);
        assertThat(sum.skipped()).isEqualTo(6);
        assertThat(sum.amendedPlayed()).isEqualTo(3);
        assertThat(sum.receivedAt()).isEqualTo(one.receivedAt());
    }

    @Test
    void unitReportsThatAgreeKeepTheirStatusInTheSum() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null);
        RunUnit first = unit(run, 0, G1);
        RunUnit second = unit(run, 1, G2);

        assertThat(detail(run, List.of(first, second), List.of(), List.of(),
                List.of(report(run, first, "FAILED", 1, 0, 0), report(run, second, "FAILED", 1, 0, 0)))
                .importReport().status()).isEqualTo("FAILED");
        assertThat(detail(run, List.of(first, second), List.of(), List.of(), List.of()).importReport()).isNull();
    }

    @Test
    void eachUnitDetailHoldsItsOwnStepsArtifactsFolderAndPackageUrl() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null);
        RunUnit first = unit(run, 0, G1);
        RunUnit second = unit(run, 1, G2);
        PipelineStep ingestOne = step(run, first, StepKind.INGEST, 1);
        PipelineStep ingestTwo = step(run, second, StepKind.INGEST, 1);
        RunArtifact zipOne = new RunArtifact(UUID.randomUUID(), run.id(), first.id(), ArtifactKind.ZIP,
                "bcnesa/2026-2027/" + run.id() + "/0-abc/ingest-ing1.zip", SHA, 5, T0);
        RunArtifact purgedTwo = new RunArtifact(UUID.randomUUID(), run.id(), second.id(), ArtifactKind.ZIP,
                "bcnesa/2026-2027/" + run.id() + "/1-def/ingest-ing2.zip", SHA, 6, T0, T0.plusSeconds(30));

        RunDetailDto detail = detail(run, List.of(first, second), List.of(ingestOne, ingestTwo),
                List.of(zipOne, purgedTwo), List.of());

        RunUnitDetailDto one = detail.units().get(0);
        assertThat(one.steps()).extracting(StepDto::unitId).containsExactly(first.id());
        assertThat(one.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.unitId()).isEqualTo(first.id());
            assertThat(artifact.purgedAt()).isNull();
        });
        assertThat(one.storageFolder()).isEqualTo("bcnesa/2026-2027/" + run.id() + "/0-abc");
        assertThat(one.packageUrl()).isEqualTo("/api/pipeline/runs/" + run.id() + "/units/" + first.id() + "/package");
        RunUnitDetailDto two = detail.units().get(1);
        assertThat(two.steps()).extracting(StepDto::unitId).containsExactly(second.id());
        assertThat(two.storageFolder()).isEqualTo("bcnesa/2026-2027/" + run.id() + "/1-def");
        assertThat(two.packageUrl()).isNull();
        assertThat(two.artifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact.purgedAt()).isEqualTo(T0.plusSeconds(30)));
        assertThat(detail.artifacts()).hasSize(2);
        assertThat(detail.steps()).hasSize(2);
    }

    @Test
    void aUnitWithoutAPackageHasNoFolderAndNoUrl() {
        PipelineRun run = run();

        RunUnitDetailDto unit = detail(run, List.of(unit(run, 0, G1)), List.of(), List.of(), List.of()).units().get(0);

        assertThat(unit.storageFolder()).isNull();
        assertThat(unit.packageUrl()).isNull();
        assertThat(unit.artifacts()).isEmpty();
    }

    @Test
    void theRetryDecisionAndTheRetryRunsOfAUnitComeFromTheCore() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0)
                .finish(RunStatus.PARTIAL, null, T0.plusSeconds(9));
        RunUnit failed = unit(run, 0, G1).startIngest("i", T0).fail(new RunError("INGEST_FAILED", "boom"), T0);
        RunUnit done = unit(run, 1, G2).startIngest("i", T0).noChanges(T0);
        PipelineRun retry = scopedRun(RunTrigger.UNIT_RETRY, run.id(), failed.id());

        RunDetailDto detail = mapper.detail(run, List.of(failed, done), List.of(), List.of(), List.of(),
                ReplayEligibility.yes(),
                Map.of(failed.id(), UnitRetryEligibility.yes(),
                        done.id(), UnitRetryEligibility.no(UnitRetryEligibility.UNIT_NOT_RETRYABLE)),
                Map.of(failed.id(), List.of(retry)));

        assertThat(detail.units().get(0).retry()).isEqualTo(new RunUnitDetailDto.Retry(true, null));
        assertThat(detail.units().get(1).retry()).isEqualTo(new RunUnitDetailDto.Retry(false, "UNIT_NOT_RETRYABLE"));
        assertThat(detail.units().get(0).retriedBy())
                .containsExactly(new RunUnitDetailDto.RetryRef(retry.id(), "QUEUED"));
        assertThat(detail.units().get(1).retriedBy()).isEmpty();
    }

    @Test
    void detailExposesTheReplayEligibilityAndPurgedArtifacts() {
        PipelineRun run = run();
        RunUnit unit = unit(run, 0, G1);
        RunArtifact purged = new RunArtifact(UUID.randomUUID(), run.id(), unit.id(), ArtifactKind.ZIP, "k/k.zip", SHA,
                5, T0, T0.plusSeconds(30));

        RunDetailDto detail = mapper.detail(run, List.of(unit), List.of(), List.of(purged), List.of(),
                ReplayEligibility.no(ReplayEligibility.ARTIFACT_PURGED), Map.of(), Map.of());

        assertThat(detail.replay()).isEqualTo(new ReplayDto(false, "ARTIFACT_PURGED"));
        assertThat(detail.artifacts()).singleElement().satisfies(artifact ->
                assertThat(artifact.purgedAt()).isEqualTo(T0.plusSeconds(30)));
        assertThat(detail(run, List.of(unit), List.of(), List.of(), List.of()).replay())
                .isEqualTo(new ReplayDto(true, null));
    }

    @Test
    void theDetailDoesNotRepeatTheUnitsInTheRunSummaryButKeepsTheSingleUnitIds() {
        PipelineRun run = run().start(T0);
        RunUnit unit = unit(run, 0, G1).startIngest("ing1", T0);

        RunDetailDto detail = detail(run, List.of(unit), List.of(), List.of(), List.of());

        assertThat(detail.run().units()).isNull();
        assertThat(detail.run().ingestRunId()).isEqualTo("ing1");
        assertThat(detail.run().currentUnitId()).isEqualTo(unit.id());
        assertThat(detail.units()).hasSize(1);
    }

    @Test
    void issuesCollectReportIssuesAndUnitErrorsWithoutDuplicates() {
        PipelineRun run = scopedRun(RunTrigger.MANUAL, null, null).start(T0);
        RunUnit first = unit(run, 0, G1).startIngest("i", T0).fail(new RunError("INGEST_FAILED", "parser exploded"),
                T0.plusSeconds(1));
        RunUnit second = unit(run, 1, G2).startIngest("i", T0).packed(T0).startImport(UUID.randomUUID(), T0)
                .partial(T0.plusSeconds(2));
        PipelineRun finished = run.finish(RunStatus.PARTIAL, null, T0.plusSeconds(3));

        List<String> issues = detail(finished, List.of(first, second), List.of(), List.of(),
                List.of(report(finished, second, "PARTIAL", 1, 1, 0, "odd acta"))).issues();

        assertThat(issues).containsExactly("SENIOR G2: odd acta", "SENIOR G1: parser exploded");
    }

    @Test
    void aSingleUnitFailureIsReportedOnceEvenThoughTheRunRepeatsItsError() {
        PipelineRun run = run().start(T0);
        RunError error = new RunError("IMPORT_FAILED", "database unavailable");
        RunUnit unit = unit(run, 0, G1).startIngest("i", T0).fail(error, T0.plusSeconds(1));
        PipelineRun failed = run.finish(RunStatus.FAILED, error, T0.plusSeconds(2));

        assertThat(detail(failed, List.of(unit), List.of(), List.of(), List.of()).issues())
                .containsExactly("database unavailable");
    }

    @Test
    void aFailedRunWithoutUnitsStillReportsItsError() {
        PipelineRun failed = run().fail(new RunError("DISPATCH_FAILED", "no thread"), T0.plusSeconds(1));

        RunDetailDto detail = detail(failed, List.of(), List.of(), List.of(), List.of());

        assertThat(detail.issues()).containsExactly("no thread");
        assertThat(detail.units()).isEmpty();
    }
}
