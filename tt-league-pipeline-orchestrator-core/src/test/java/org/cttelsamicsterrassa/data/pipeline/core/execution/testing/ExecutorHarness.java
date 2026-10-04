package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.PollIntervals;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RetryPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import java.time.Duration;
import java.util.UUID;

/** An executor wired to the in-memory repositories, with the production defaults. */
public class ExecutorHarness {

    public static final ExecutionSettings DEFAULT_SETTINGS = new ExecutionSettings(
            new RetryPolicy(3, Duration.ofSeconds(30), 2, Duration.ofMinutes(5)),
            new StepTimeouts(Duration.ofHours(3), Duration.ofMinutes(10), Duration.ofHours(3)),
            new PollIntervals(Duration.ofSeconds(15), Duration.ofSeconds(10)));

    public final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    public final InMemoryPipelineStepRepository steps = new InMemoryPipelineStepRepository();
    public final InMemoryRunArtifactRepository artifactRows = new InMemoryRunArtifactRepository();
    public final InMemoryImportReportRepository reports = new InMemoryImportReportRepository();
    public final RecordingObserver observer = new RecordingObserver();
    public final RunClock clock;
    public final FakeRunClock fakeClock;
    public final RunExecutor executor;

    public ExecutorHarness(IngestGateway ingest, ImportGateway importGateway, ArtifactStore artifacts) {
        this(ingest, importGateway, artifacts, new FakeRunClock(), DEFAULT_SETTINGS);
    }

    public ExecutorHarness(IngestGateway ingest, ImportGateway importGateway, ArtifactStore artifacts,
            FakeRunClock clock, ExecutionSettings settings) {
        this.fakeClock = clock;
        this.clock = clock;
        this.executor = new RunExecutor(runs, steps, artifactRows, reports, ingest, importGateway, artifacts, clock,
                observer, settings);
    }

    public PipelineRun queueRun(PipelineSource source, RunScope scope) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", scope, RunTrigger.MANUAL,
                "tester", null, clock.now()));
    }

    public PipelineRun queueRun() {
        return queueRun(PipelineSource.RFETM, RunScope.fullSeason());
    }

    public PipelineRun run(UUID id) {
        return runs.findById(id).orElseThrow();
    }
}
