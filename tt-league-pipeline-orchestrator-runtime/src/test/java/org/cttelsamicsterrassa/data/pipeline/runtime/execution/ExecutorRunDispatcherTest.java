package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.PollIntervals;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RetryPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.FetchedPackage;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.PackageSink;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ExecutorRunDispatcherTest {

    private static final ExecutionSettings SETTINGS = new ExecutionSettings(
            new RetryPolicy(3, Duration.ofSeconds(30), 2, Duration.ofMinutes(5)),
            new StepTimeouts(Duration.ofHours(3), Duration.ofMinutes(10), Duration.ofHours(3)),
            new PollIntervals(Duration.ofHours(1), Duration.ofHours(1)));

    /** Blocks in startRun until released; polls report NO_CHANGES (or RUNNING for the sleeping test). */
    private static final class BlockingIngest implements IngestGateway {

        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        final List<String> loggedRunIds = Collections.synchronizedList(new ArrayList<>());
        final boolean finishRuns;

        BlockingIngest(boolean finishRuns) {
            this.finishRuns = finishRuns;
        }

        @Override
        public String startRun(IngestRunRequest request) {
            starts.incrementAndGet();
            threadNames.add(Thread.currentThread().getName());
            loggedRunIds.add(String.valueOf(MDC.get("runId")));
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                active.decrementAndGet();
            }
            return "ing-" + request.source();
        }

        @Override
        public IngestRunState getRun(String ingestRunId) {
            return finishRuns
                    ? new IngestRunState(ingestRunId, "SUCCEEDED", "NO_CHANGES", false, false, null)
                    : new IngestRunState(ingestRunId, "RUNNING", null, false, false, null);
        }

        @Override
        public FetchedPackage fetchPackage(String ingestRunId, PackageSink sink) {
            throw new AssertionError("not expected");
        }
    }

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryPipelineStepRepository steps = new InMemoryPipelineStepRepository();
    private ExecutorRunDispatcher dispatcher;

    private ExecutorRunDispatcher dispatcher(BlockingIngest ingest, int concurrency) {
        RunExecutor executor = new RunExecutor(runs, steps, new InMemoryRunArtifactRepository(),
                new InMemoryImportReportRepository(), ingest, new ScriptedImportGateway(),
                new InMemoryArtifactStore(), new SystemRunClock(), RunObserver.none(), SETTINGS);
        dispatcher = new ExecutorRunDispatcher(executor, concurrency);
        return dispatcher;
    }

    private PipelineRun queue(PipelineSource source) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "tester", null, java.time.Instant.now()));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 10 s");
            }
            Thread.sleep(10);
        }
    }

    @AfterEach
    void stop() throws InterruptedException {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    void runsOnNamedPlatformThreads() throws Exception {
        BlockingIngest ingest = new BlockingIngest(true);
        ingest.release.countDown();
        PipelineRun run = queue(PipelineSource.RFETM);

        dispatcher(ingest, 2).dispatch(run.id());

        await(() -> runs.findById(run.id()).orElseThrow().status() == RunStatus.NO_CHANGES);
        assertThat(ingest.threadNames).singleElement().asString().matches("pipeline-run-\\d+");
    }

    @Test
    void everyRunExecutesWithItsOwnRunIdInTheLogContext() throws Exception {
        BlockingIngest ingest = new BlockingIngest(true);
        ingest.release.countDown();
        PipelineRun first = queue(PipelineSource.RFETM);
        PipelineRun second = queue(PipelineSource.BCNESA);
        ExecutorRunDispatcher d = dispatcher(ingest, 1);

        d.dispatch(first.id());
        await(() -> runs.findById(first.id()).orElseThrow().status() == RunStatus.NO_CHANGES);
        d.dispatch(second.id());
        await(() -> runs.findById(second.id()).orElseThrow().status() == RunStatus.NO_CHANGES);

        assertThat(ingest.threadNames).hasSize(2).containsOnly(ingest.threadNames.get(0));
        assertThat(ingest.loggedRunIds).containsExactly(first.id().toString(), second.id().toString());
        assertThat(MDC.get("runId")).isNull();
    }

    @Test
    void aSecondDispatchOfARunThatIsExecutingIsIgnored() throws Exception {
        BlockingIngest ingest = new BlockingIngest(true);
        PipelineRun run = queue(PipelineSource.RFETM);
        ExecutorRunDispatcher d = dispatcher(ingest, 3);

        d.dispatch(run.id());
        await(() -> ingest.starts.get() == 1);
        d.dispatch(run.id());
        Thread.sleep(200);
        ingest.release.countDown();

        await(() -> runs.findById(run.id()).orElseThrow().status() == RunStatus.NO_CHANGES);
        assertThat(ingest.starts.get()).isEqualTo(1);
    }

    @Test
    void neverRunsMoreThanMaxConcurrentRunsAtOnce() throws Exception {
        BlockingIngest ingest = new BlockingIngest(true);
        List<PipelineRun> queued = List.of(queue(PipelineSource.RFETM), queue(PipelineSource.BCNESA),
                queue(PipelineSource.FCTT));
        ExecutorRunDispatcher d = dispatcher(ingest, 2);

        queued.forEach(run -> d.dispatch(run.id()));
        await(() -> ingest.active.get() == 2);
        Thread.sleep(200);
        assertThat(ingest.active.get()).isEqualTo(2);
        assertThat(ingest.starts.get()).isEqualTo(2);
        ingest.release.countDown();

        await(() -> queued.stream().allMatch(
                run -> runs.findById(run.id()).orElseThrow().status() == RunStatus.NO_CHANGES));
        assertThat(ingest.maxActive.get()).isEqualTo(2);
    }

    @Test
    void shutdownInterruptsASleepingRunWithoutFailingIt() throws Exception {
        BlockingIngest ingest = new BlockingIngest(false);
        ingest.release.countDown();
        PipelineRun run = queue(PipelineSource.RFETM);
        ExecutorRunDispatcher d = dispatcher(ingest, 1);

        d.dispatch(run.id());
        await(() -> runs.findById(run.id()).orElseThrow().status() == RunStatus.RUNNING_INGEST);
        Thread.sleep(200);
        d.shutdown();

        assertThat(runs.findById(run.id()).orElseThrow().status()).isEqualTo(RunStatus.RUNNING_INGEST);
        assertThat(steps.findByRunId(run.id())).singleElement()
                .satisfies(step -> assertThat(step.status()).isEqualTo(StepStatus.RUNNING));
    }
}
