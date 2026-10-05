package org.cttelsamicsterrassa.data.pipeline.runtime.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.junit.jupiter.api.Test;

class RunMetricsObserverTest {

    private static final Instant T0 = Instant.parse("2026-10-05T10:00:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final RunMetricsObserver observer = new RunMetricsObserver(registry, runs);

    private PipelineRun queued(PipelineSource source, RunTrigger trigger) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), false,
                trigger, "tester", trigger == RunTrigger.RETRY ? UUID.randomUUID() : null, T0));
    }

    private double finished(String source, String trigger, String outcome, String error) {
        var counter = registry.find("pipeline.runs.finished")
                .tags("source", source, "trigger", trigger, "outcome", outcome, "error", error).counter();
        return counter == null ? 0 : counter.count();
    }

    private Timer stepTimer(String step, String status) {
        return registry.get("pipeline.step.duration").tags("source", "BCNESA", "step", step, "status", status)
                .timer();
    }

    private static PipelineStep step(PipelineRun run, StepKind kind, int attempt) {
        return PipelineStep.start(UUID.randomUUID(), run.id(), kind, attempt, T0, null);
    }

    @Test
    void countsEachTerminalStatusOnceWithItsDuration() {
        PipelineRun noChanges = queued(PipelineSource.RFETM, RunTrigger.SCHEDULED)
                .startIngest("i1", T0).noChanges(T0.plusSeconds(20));
        PipelineRun succeeded = queued(PipelineSource.BCNESA, RunTrigger.MANUAL)
                .startIngest("i2", T0).packed(T0.plusSeconds(5)).startImport(UUID.randomUUID(), T0.plusSeconds(6))
                .succeed(T0.plusSeconds(120));
        PipelineRun partial = queued(PipelineSource.FCTT, RunTrigger.RETRY)
                .startIngest("i3", T0).packed(T0.plusSeconds(5)).startImport(UUID.randomUUID(), T0.plusSeconds(6))
                .partial(T0.plusSeconds(60));

        observer.runChanged(noChanges);
        observer.runChanged(succeeded);
        observer.runChanged(partial);

        assertThat(finished("RFETM", "SCHEDULED", "NO_CHANGES", "none")).isEqualTo(1);
        assertThat(finished("BCNESA", "MANUAL", "SUCCEEDED", "none")).isEqualTo(1);
        assertThat(finished("FCTT", "RETRY", "PARTIAL", "none")).isEqualTo(1);
        Timer timer = registry.get("pipeline.run.duration")
                .tags("source", "BCNESA", "trigger", "MANUAL", "outcome", "SUCCEEDED").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(120);
    }

    @Test
    void aFailedRunCarriesItsErrorCode() {
        PipelineRun failed = queued(PipelineSource.RFETM, RunTrigger.MANUAL).startIngest("i1", T0)
                .fail(new RunError("INGEST_TIMEOUT", "took too long"), T0.plusSeconds(30));

        observer.runChanged(failed);

        assertThat(finished("RFETM", "MANUAL", "FAILED", "INGEST_TIMEOUT")).isEqualTo(1);
        assertThat(registry.get("pipeline.run.duration").tag("outcome", "FAILED").timer().count()).isEqualTo(1);
    }

    @Test
    void aLaunchFailureIsCountedWithoutADuration() {
        PipelineRun failed = queued(PipelineSource.FCTT, RunTrigger.SCHEDULED)
                .fail(new RunError("LAUNCH_FAILED", "no thread"), T0.plusSeconds(1));
        assertThat(failed.startedAt()).isNull();

        observer.runChanged(failed);

        assertThat(finished("FCTT", "SCHEDULED", "FAILED", "LAUNCH_FAILED")).isEqualTo(1);
        assertThat(registry.find("pipeline.run.duration").timer()).isNull();
    }

    @Test
    void nonTerminalRunChangesAndRunningStepsAreIgnored() {
        PipelineRun run = queued(PipelineSource.RFETM, RunTrigger.MANUAL);

        observer.runChanged(run);
        observer.runChanged(run.startIngest("i1", T0));
        observer.stepChanged(step(run, StepKind.INGEST, 1));

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void recordsOneStepSamplePerAttemptByKindAndStatus() {
        PipelineRun run = queued(PipelineSource.BCNESA, RunTrigger.MANUAL);
        PipelineStep failedAttempt = step(run, StepKind.INGEST, 1)
                .fail(T0.plusSeconds(10), "SOURCE_UNAVAILABLE", new RunError("SOURCE_UNAVAILABLE", "down"), true);
        PipelineStep okAttempt = step(run, StepKind.INGEST, 2).succeed(T0.plusSeconds(90), "SUCCEEDED");
        PipelineStep fetch = step(run, StepKind.FETCH_PACKAGE, 1).succeed(T0.plusSeconds(3), "OK");
        PipelineStep imported = step(run, StepKind.IMPORT, 1).succeed(T0.plusSeconds(300), "OK");

        for (PipelineStep finishedStep : new PipelineStep[] {failedAttempt, okAttempt, fetch, imported}) {
            observer.stepChanged(finishedStep);
        }

        assertThat(stepTimer("INGEST", "FAILED").totalTime(TimeUnit.SECONDS)).isEqualTo(10);
        assertThat(stepTimer("INGEST", "SUCCEEDED").totalTime(TimeUnit.SECONDS)).isEqualTo(90);
        assertThat(stepTimer("FETCH_PACKAGE", "SUCCEEDED").count()).isEqualTo(1);
        assertThat(stepTimer("IMPORT", "SUCCEEDED").totalTime(TimeUnit.SECONDS)).isEqualTo(300);
    }

    @Test
    void noMeterTagCarriesARunIdOrFreeText() {
        PipelineRun run = queued(PipelineSource.RFETM, RunTrigger.MANUAL).startIngest("ing-1", T0)
                .fail(new RunError("IMPORT_FAILED", "secret message"), T0.plusSeconds(5));
        observer.runChanged(run);
        observer.stepChanged(step(run, StepKind.INGEST, 1).succeed(T0.plusSeconds(5), "OK"));

        assertThat(registry.getMeters()).isNotEmpty();
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag ->
                assertThat(tag.getValue()).doesNotContain(run.id().toString()).doesNotContain("secret")));
    }

    @Test
    void aStepOfAMissingRunFailsAndRecordsNothing() {
        PipelineStep orphan = PipelineStep.start(UUID.randomUUID(), UUID.randomUUID(), StepKind.INGEST, 1, T0, null)
                .succeed(T0.plusSeconds(1), "OK");

        assertThatThrownBy(() -> observer.stepChanged(orphan)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void durationTimersPublishTheFixedBuckets() {
        PipelineRun run = queued(PipelineSource.RFETM, RunTrigger.MANUAL).startIngest("i", T0)
                .noChanges(T0.plus(Duration.ofMinutes(2)));

        observer.runChanged(run);

        Timer timer = registry.get("pipeline.run.duration").timer();
        assertThat(timer.takeSnapshot().histogramCounts()).hasSize(10);
    }
}
