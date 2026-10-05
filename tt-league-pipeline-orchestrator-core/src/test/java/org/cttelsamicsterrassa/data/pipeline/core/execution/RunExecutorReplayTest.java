package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.counters;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.created;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.existing;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.job;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.season;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.finished;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.PackageResponse;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.junit.jupiter.api.Test;

class RunExecutorReplayTest {

    private static final byte[] ZIP = "zip-content".getBytes(StandardCharsets.UTF_8);

    private final ScriptedIngestGateway ingest = new ScriptedIngestGateway();
    private final ScriptedImportGateway platform = new ScriptedImportGateway();
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final ExecutorHarness h = new ExecutorHarness(ingest, platform, store);
    private final UUID originalJob = UUID.randomUUID();
    private final UUID replayJob = UUID.randomUUID();

    /** Runs an original through ingest and an import that ends with the given status. */
    private PipelineRun originalEnding(String importStatus) {
        ingest.start("ing1").poll(finished("ing1", "SUCCEEDED", true)).packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(originalJob))
                .poll(job(originalJob, importStatus, "FAILED".equals(importStatus) ? "boom" : null,
                        season("2025-2026", importStatus, counters(1, 1))));
        PipelineRun queued = h.queueRun();
        h.executor.execute(queued.id());
        return h.run(queued.id());
    }

    private PipelineRun queueReplay(PipelineRun original) {
        return h.runs.create(PipelineRun.queue(UUID.randomUUID(), original.source(), original.season(),
                original.scope(), original.force(), RunTrigger.RETRY, "operator", original.id(), h.clock.now()));
    }

    private PipelineRun replay(PipelineRun replay) {
        h.executor.execute(replay.id());
        return h.run(replay.id());
    }

    private PipelineStep importStep(PipelineRun run) {
        List<PipelineStep> attempts = h.steps.findByRunId(run.id()).stream()
                .filter(step -> step.kind() == StepKind.IMPORT)
                .toList();
        return attempts.get(attempts.size() - 1);
    }

    @Test
    void replayOfAFailedImportResubmitsTheStoredZipWithoutCallingIngest() {
        PipelineRun original = originalEnding("FAILED");
        assertThat(original.status()).isEqualTo(RunStatus.FAILED);
        int ingestCalls = ingest.startRequests.size();
        platform.submit(created(replayJob)).poll(job(replayJob, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(2, 3))));

        PipelineRun result = replay(queueReplay(original));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.trigger()).isEqualTo(RunTrigger.RETRY);
        assertThat(result.ingestRunId()).isNull();
        assertThat(result.importJobId()).isEqualTo(replayJob);
        assertThat(ingest.startRequests).hasSize(ingestCalls);
        assertThat(h.steps.findByRunId(result.id())).extracting(PipelineStep::kind).containsExactly(StepKind.IMPORT);
        assertThat(importStep(result).importJobReused()).isFalse();
        assertThat(platform.submissions).hasSize(2);
        assertThat(platform.submissions.get(1).bytes()).isEqualTo(ZIP);
        assertThat(platform.submissions.get(1).clientRunId()).isEqualTo(result.id());
        assertThat(h.reports.findByRunId(result.id())).isPresent();
    }

    @Test
    void replayCopiesTheZipRowAndSharesTheFile() {
        PipelineRun original = originalEnding("FAILED");
        platform.submit(created(replayJob)).poll(job(replayJob, "SUCCEEDED", null));

        PipelineRun result = replay(queueReplay(original));

        RunArtifact source = h.artifactRows.findByRunId(original.id()).get(0);
        assertThat(h.artifactRows.findByRunId(result.id())).singleElement().satisfies(copy -> {
            assertThat(copy.id()).isNotEqualTo(source.id());
            assertThat(copy.kind()).isEqualTo(ArtifactKind.ZIP);
            assertThat(copy.storageKey()).isEqualTo(source.storageKey());
            assertThat(copy.sha256()).isEqualTo(source.sha256());
            assertThat(copy.sizeBytes()).isEqualTo(source.sizeBytes());
        });
        assertThat(h.artifactRows.findByStorageKey(source.storageKey())).hasSize(2);
    }

    @Test
    void existingJobIsRecordedAsReusedAndNothingIsReimported() {
        PipelineRun original = originalEnding("SUCCEEDED");
        platform.submit(existing(originalJob)).poll(job(originalJob, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));

        PipelineRun result = replay(queueReplay(original));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.importJobId()).isEqualTo(originalJob);
        PipelineStep step = importStep(result);
        assertThat(step.importJobReused()).isTrue();
        assertThat(step.externalRef()).isEqualTo(originalJob.toString());
        assertThat(h.reports.findByRunId(result.id())).isPresent();
    }

    @Test
    void normalSubmissionRecordsAFreshJob() {
        PipelineRun original = originalEnding("SUCCEEDED");

        assertThat(importStep(original).importJobReused()).isFalse();
    }

    @Test
    void partialAndFailedImportResultsEndTheReplayAccordingly() {
        PipelineRun original = originalEnding("FAILED");
        platform.submit(created(replayJob)).poll(job(replayJob, "PARTIAL", null,
                season("2025-2026", "PARTIAL", counters(1, 1))));
        assertThat(replay(queueReplay(original)).status()).isEqualTo(RunStatus.PARTIAL);

        UUID failingJob = UUID.randomUUID();
        platform.submit(created(failingJob)).poll(job(failingJob, "FAILED", "still broken"));
        PipelineRun failed = replay(queueReplay(original));
        assertThat(failed.status()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.error().code()).isEqualTo("IMPORT_FAILED");
    }

    @Test
    void missingFileFailsTheReplayWithArtifactPurgedAndNoRetry() {
        PipelineRun original = originalEnding("FAILED");
        store.delete(h.artifactRows.findByRunId(original.id()).get(0).storageKey());

        PipelineRun result = replay(queueReplay(original));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("ARTIFACT_PURGED");
        assertThat(h.steps.findByRunId(result.id())).isEmpty();
        assertThat(h.artifactRows.findByRunId(result.id())).isEmpty();
        assertThat(platform.submissions).hasSize(1);
    }

    @Test
    void purgedRowFailsTheReplayWithArtifactPurged() {
        PipelineRun original = originalEnding("FAILED");
        h.artifactRows.markPurged(h.artifactRows.findByRunId(original.id()).get(0).storageKey(), h.clock.now());

        PipelineRun result = replay(queueReplay(original));

        assertThat(result.error().code()).isEqualTo("ARTIFACT_PURGED");
    }

    @Test
    void changedFileFailsTheReplayWithAChecksumMismatch() {
        PipelineRun original = originalEnding("FAILED");
        String key = h.artifactRows.findByRunId(original.id()).get(0).storageKey();
        store.delete(key);
        store.store(key, new ByteArrayInputStream("tampered".getBytes(StandardCharsets.UTF_8)));

        PipelineRun result = replay(queueReplay(original));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("PACKAGE_CHECKSUM_MISMATCH");
        assertThat(platform.submissions).hasSize(1);
    }

    @Test
    void resumesAfterACrashBetweenTheRowCopyAndTheStartOfTheReplay() {
        PipelineRun original = originalEnding("FAILED");
        PipelineRun queued = queueReplay(original);
        RunArtifact source = h.artifactRows.findByRunId(original.id()).get(0);
        h.artifactRows.add(new RunArtifact(UUID.randomUUID(), queued.id(), ArtifactKind.ZIP, source.storageKey(),
                source.sha256(), source.sizeBytes(), h.clock.now()));
        platform.submit(created(replayJob)).poll(job(replayJob, "SUCCEEDED", null));

        PipelineRun result = replay(queued);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(h.artifactRows.findByRunId(result.id())).hasSize(1);
    }

    @Test
    void resumesWhileImportingByPollingTheStoredJob() {
        PipelineRun original = originalEnding("FAILED");
        PipelineRun queued = queueReplay(original);
        RunArtifact source = h.artifactRows.findByRunId(original.id()).get(0);
        h.artifactRows.add(new RunArtifact(UUID.randomUUID(), queued.id(), ArtifactKind.ZIP, source.storageKey(),
                source.sha256(), source.sizeBytes(), h.clock.now()));
        PipelineRun packed = h.runs.update(queued.startReplay(h.clock.now()));
        h.steps.save(PipelineStep.start(UUID.randomUUID(), packed.id(), StepKind.IMPORT, 1, h.clock.now(), null)
                .withImportJob(replayJob, false));
        h.runs.update(packed.startImport(replayJob, h.clock.now()));
        platform.poll(job(replayJob, "SUCCEEDED", null));

        PipelineRun result = replay(packed);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(platform.submissions).hasSize(1);
        assertThat(h.steps.findByRunId(result.id())).allMatch(step -> step.status() == StepStatus.SUCCEEDED);
    }

    @Test
    void replayOfAReplayUsesItsOwnZipRow() {
        PipelineRun original = originalEnding("FAILED");
        platform.submit(created(replayJob)).poll(job(replayJob, "SUCCEEDED", null));
        PipelineRun first = replay(queueReplay(original));
        UUID secondJob = UUID.randomUUID();
        platform.submit(existing(secondJob)).poll(job(secondJob, "SUCCEEDED", null));

        PipelineRun second = replay(h.runs.create(PipelineRun.queue(UUID.randomUUID(), first.source(),
                first.season(), RunScope.fullSeason(), false, RunTrigger.RETRY, "operator", first.id(),
                h.clock.now())));

        assertThat(second.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(h.artifactRows.findByStorageKey(h.artifactRows.findByRunId(first.id()).get(0).storageKey()))
                .hasSize(3);
        assertThat(PipelineSource.RFETM).isEqualTo(second.source());
    }
}
