package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Commits for real (no rollback-only tests) so unique-index behaviour is visible; truncates between tests. */
@PipelinePersistenceTest
abstract class AbstractPersistenceTest {

    static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RunUnitRepository unitRepo;

    @BeforeEach
    void truncateTables() {
        jdbc.execute("TRUNCATE pipeline.daily_stats, pipeline.alert, pipeline.poll_schedule, pipeline.poll_policy, pipeline.match_day, pipeline.pending_trigger, pipeline.import_report, pipeline.run_artifact, pipeline.pipeline_step, "
                + "pipeline.pipeline_unit, pipeline.pipeline_run CASCADE");
    }

    /** Stores the season unit (ordinal 0) of the run; steps, artifacts and reports reference a unit. */
    RunUnit unit(PipelineRun run) {
        return unitRepo.addAll(UnitPlanner.plan(run.id(), RunScope.fullSeason())).get(0);
    }

    /** Stores one unit per ingest group of the run, in the given order. */
    List<RunUnit> units(PipelineRun run, ScopeFilter... filters) {
        return unitRepo.addAll(UnitPlanner.plan(run.id(), new RunScope(List.of(filters))));
    }

    static PipelineRun queued(PipelineSource source) {
        return PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), false, RunTrigger.MANUAL,
                "user-1", null, T0);
    }
}
