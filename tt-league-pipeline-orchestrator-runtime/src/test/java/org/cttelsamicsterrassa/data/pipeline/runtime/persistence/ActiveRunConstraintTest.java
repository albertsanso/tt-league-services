package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ActiveRunConstraintTest extends AbstractPersistenceTest {

    @Autowired
    PipelineRunRepository runs;

    @Test
    void secondActiveRunForSameSourceIsRejected() {
        runs.create(queued(PipelineSource.RFETM));

        assertThatThrownBy(() -> runs.create(queued(PipelineSource.RFETM)))
                .isInstanceOf(ActiveRunConflictException.class)
                .hasMessageContaining("RFETM")
                .extracting(e -> ((ActiveRunConflictException) e).source())
                .isEqualTo(PipelineSource.RFETM);
    }

    @Test
    void runForAnotherSourceIsAccepted() {
        runs.create(queued(PipelineSource.RFETM));
        assertThat(runs.create(queued(PipelineSource.BCNESA)).status()).isEqualTo(RunStatus.QUEUED);
    }

    @Test
    void newRunIsAcceptedAfterEachTerminalState() {
        List<UnaryOperator<PipelineRun>> terminals = List.of(
                run -> run.start(T0.plusSeconds(1)).finish(RunStatus.NO_CHANGES, null, T0.plusSeconds(2)),
                run -> run.start(T0.plusSeconds(1)).finish(RunStatus.SUCCEEDED, null, T0.plusSeconds(2)),
                run -> run.start(T0.plusSeconds(1)).finish(RunStatus.PARTIAL, null, T0.plusSeconds(2)),
                run -> run.fail(new RunError("E", "failed"), T0.plusSeconds(1)));

        for (UnaryOperator<PipelineRun> terminal : terminals) {
            PipelineRun created = runs.create(queued(PipelineSource.FCTT));
            PipelineRun finished = runs.update(terminal.apply(created));
            assertThat(finished.status().isTerminal()).isTrue();
            assertThat(runs.findActiveBySource(PipelineSource.FCTT)).isEmpty();
        }
        assertThat(runs.create(queued(PipelineSource.FCTT)).status()).isEqualTo(RunStatus.QUEUED);
    }

    @Test
    void databaseIndexRejectsTwoActiveRowsIndependentlyOfTheAdapter() {
        insertActive("RUNNING");
        assertThatThrownBy(() -> insertActive("QUEUED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsTheStatusesThatMovedToTheUnits() {
        for (String legacy : List.of("RUNNING_INGEST", "PACKED", "IMPORTING")) {
            assertThatThrownBy(() -> insertActive(legacy)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void concurrentCreatesEndWithExactlyOneRun() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        runs.create(queued(PipelineSource.BCNESA));
                        return true;
                    } catch (ActiveRunConflictException e) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int created = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        Integer stored = jdbc.queryForObject(
                "SELECT count(*) FROM pipeline.pipeline_run WHERE source = 'BCNESA'", Integer.class);
        assertThat(stored).isEqualTo(1);
    }

    private void insertActive(String status) {
        jdbc.update(
                "INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, status, "
                        + "created_at, started_at, import_job_id) VALUES (?, 'RFETM', '2025-2026', "
                        + "'{\"scopes\":[]}'::jsonb, 'MANUAL', 'u', ?, now(), now(), NULL)",
                UUID.randomUUID(), status);
    }
}
