package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Commits for real (no rollback-only tests) so unique-index behaviour is visible; truncates between tests. */
@PipelinePersistenceTest
abstract class AbstractPersistenceTest {

    static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void truncateTables() {
        jdbc.execute("TRUNCATE pipeline.daily_stats, pipeline.alert, pipeline.poll_schedule, pipeline.poll_policy, pipeline.match_day, pipeline.pending_trigger, pipeline.import_report, pipeline.run_artifact, pipeline.pipeline_step, "
                + "pipeline.pipeline_run CASCADE");
    }

    static PipelineRun queued(PipelineSource source) {
        return PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), false, RunTrigger.MANUAL,
                "user-1", null, T0);
    }
}
