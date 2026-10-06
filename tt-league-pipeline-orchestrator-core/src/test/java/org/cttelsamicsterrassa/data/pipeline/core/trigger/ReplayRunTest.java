package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.junit.jupiter.api.Test;

class ReplayRunTest {

    private static final byte[] ZIP = "zip".getBytes(StandardCharsets.UTF_8);
    private static final String KEY = "rfetm/2025-2026/run/ingest-1.zip";

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryRunArtifactRepository artifactRows = new InMemoryRunArtifactRepository();
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final FakeRunClock clock = new FakeRunClock();
    private final ReplayRun replay = new ReplayRun(runs, artifactRows, store,
            new RunLauncher(runs, dispatcher, clock, new RecordingObserver()));

    private PipelineRun failedOriginal(PipelineSource source) {
        PipelineRun queued = runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026",
                RunScope.fullSeason(), true, RunTrigger.MANUAL, "user", null, clock.now()));
        return runs.update(queued.fail(new RunError("IMPORT_FAILED", "boom"), clock.now()));
    }

    private void storeZip(PipelineRun run) {
        var stored = store.store(KEY, new ByteArrayInputStream(ZIP));
        artifactRows.add(new RunArtifact(UUID.randomUUID(), run.id(), UUID.randomUUID(), ArtifactKind.ZIP, KEY, stored.sha256(),
                stored.sizeBytes(), clock.now()));
    }

    @Test
    void createsAQueuedRetryRunLinkedToTheOriginalAndDispatchesIt() {
        PipelineRun original = failedOriginal(PipelineSource.RFETM);
        storeZip(original);

        var outcome = replay.replay(original.id(), "operator");

        PipelineRun created = ((ReplayRun.Created) outcome).run();
        assertThat(created.trigger()).isEqualTo(RunTrigger.RETRY);
        assertThat(created.retryOfRunId()).isEqualTo(original.id());
        assertThat(created.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(created.requestedBy()).isEqualTo("operator");
        assertThat(created.source()).isEqualTo(original.source());
        assertThat(created.season()).isEqualTo(original.season());
        assertThat(created.force()).isTrue();
        assertThat(dispatcher.dispatched).containsExactly(created.id());
    }

    @Test
    void unknownRunIsNotFound() {
        assertThat(replay.replay(UUID.randomUUID(), "operator")).isInstanceOf(ReplayRun.NotFound.class);
    }

    @Test
    void activeRunIsNotReplayable() {
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "user", null, clock.now()));
        storeZip(active);

        var outcome = (ReplayRun.NotReplayable) replay.replay(active.id(), "operator");

        assertThat(outcome.code()).isEqualTo("RUN_ACTIVE");
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void runWithoutAZipHasNoPackage() {
        PipelineRun original = failedOriginal(PipelineSource.RFETM);

        var outcome = (ReplayRun.NotReplayable) replay.replay(original.id(), "operator");

        assertThat(outcome.code()).isEqualTo("NO_PACKAGE");
    }

    @Test
    void purgedRowAndMissingFileAreBothArtifactPurged() {
        PipelineRun original = failedOriginal(PipelineSource.RFETM);
        storeZip(original);
        store.delete(KEY);
        assertThat(((ReplayRun.NotReplayable) replay.replay(original.id(), "operator")).code())
                .isEqualTo("ARTIFACT_PURGED");

        store.store(KEY, new ByteArrayInputStream(ZIP));
        artifactRows.markPurged(KEY, clock.now());
        assertThat(((ReplayRun.NotReplayable) replay.replay(original.id(), "operator")).code())
                .isEqualTo("ARTIFACT_PURGED");
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void activeRunOfTheSourceRejectsTheReplayWithoutQueueing() {
        PipelineRun original = failedOriginal(PipelineSource.RFETM);
        storeZip(original);
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "user", null, clock.now()));

        var outcome = (ReplayRun.Rejected) replay.replay(original.id(), "operator");

        assertThat(outcome.code()).isEqualTo("ACTIVE_RUN");
        assertThat(outcome.activeRunId()).isEqualTo(active.id());
        assertThat(dispatcher.dispatched).isEmpty();
    }
}
