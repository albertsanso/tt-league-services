package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ReplayEligibility;
import org.junit.jupiter.api.Test;

class RunDtoMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private final RunDtoMapper mapper = new RunDtoMapper(new FakeRunClock(T0.plusSeconds(60)));

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "alice", null, T0);
    }

    @Test
    void anIngestStepWithHealthExposesIt() {
        PipelineRun run = run();
        PipelineStep started = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 1, T0, "ing1");

        StepDto dto = mapper.step(started.succeed(T0.plusSeconds(5), "SUCCEEDED", new IngestHealth(3, 2, 1)));

        assertThat(dto.health()).isEqualTo(new StepDto.Health(3, 2, 1));
    }

    @Test
    void stepsWithoutHealthExposeNull() {
        PipelineRun run = run();
        PipelineStep ingest = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 1, T0, "ing1");
        PipelineStep importing = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, 1, T0, null);

        assertThat(mapper.step(ingest).health()).isNull();
        assertThat(mapper.step(ingest.succeed(T0.plusSeconds(5), "NO_CHANGES")).health()).isNull();
        assertThat(mapper.step(importing.succeed(T0.plusSeconds(5), "SUCCEEDED")).health()).isNull();
    }

    @Test
    void theImportReportExposesTheAmendedCount() {
        PipelineRun run = run();
        ImportReport report = new ImportReport(run.id(), UUID.randomUUID(), "SUCCEEDED", 1, 2, 3, 4, 5, 6, 7, 8, 9,
                10, 11, List.of(), "{}", T0);

        RunDetailDto detail = mapper.detail(run, List.of(), List.of(), report, ReplayEligibility.yes());

        assertThat(detail.importReport().amendedPlayed()).isEqualTo(11);
        assertThat(detail.importReport().unresolvedPendingFixtures()).isEqualTo(10);
    }

    @Test
    void detailExposesTheReplayEligibilityAndPurgedArtifacts() {
        PipelineRun run = run();
        RunArtifact purged = new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, "k.zip", "a".repeat(64),
                5, T0, T0.plusSeconds(30));

        RunDetailDto detail = mapper.detail(run, List.of(), List.of(purged), null,
                ReplayEligibility.no(ReplayEligibility.ARTIFACT_PURGED));

        assertThat(detail.replay()).isEqualTo(new ReplayDto(false, "ARTIFACT_PURGED"));
        assertThat(detail.artifacts()).singleElement().satisfies(artifact ->
                assertThat(artifact.purgedAt()).isEqualTo(T0.plusSeconds(30)));
        assertThat(mapper.detail(run, List.of(), List.of(), null, ReplayEligibility.yes()).replay())
                .isEqualTo(new ReplayDto(true, null));
    }

    @Test
    void importJobReusedComesFromTheImportStepThatSubmittedTheRunsJob() {
        UUID job = UUID.randomUUID();
        PipelineRun base = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027",
                RunScope.fullSeason(), false, RunTrigger.RETRY, "alice", UUID.randomUUID(), T0);
        PipelineRun importing = base.startReplay(T0.plusSeconds(1)).startImport(job, T0.plusSeconds(2));
        PipelineStep failedAttempt = PipelineStep.start(UUID.randomUUID(), base.id(), StepKind.IMPORT, 1, T0, null)
                .fail(T0.plusSeconds(1), null, new org.cttelsamicsterrassa.data.pipeline.core.run.RunError("X", "x"),
                        true);
        PipelineStep reused = PipelineStep.start(UUID.randomUUID(), base.id(), StepKind.IMPORT, 2, T0.plusSeconds(1),
                null).withImportJob(job, true);

        assertThat(mapper.summary(importing, List.of(failedAttempt, reused)).importJobReused()).isTrue();
        assertThat(mapper.summary(importing, null).importJobReused()).isNull();
        assertThat(mapper.summary(base, List.of(reused)).importJobReused()).isNull();
        assertThat(mapper.step(reused).importJobReused()).isTrue();
        assertThat(mapper.detail(importing, List.of(reused), List.of(), null, ReplayEligibility.yes())
                .run().importJobReused()).isTrue();
    }
}
