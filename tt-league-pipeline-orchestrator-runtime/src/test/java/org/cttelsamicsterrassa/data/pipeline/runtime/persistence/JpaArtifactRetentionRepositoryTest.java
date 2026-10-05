package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.retention.RetainedArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaArtifactRetentionRepositoryTest extends AbstractPersistenceTest {

    private static final String SHA = "0123456789abcdef".repeat(4);

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    RunArtifactRepository artifacts;

    @Autowired
    ArtifactRetentionRepository retention;

    private PipelineRun finishedRun(PipelineSource source, String season) {
        PipelineRun queued = runs.create(PipelineRun.queue(UUID.randomUUID(), source, season, RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "user-1", null, T0));
        return runs.update(queued.fail(new org.cttelsamicsterrassa.data.pipeline.core.run.RunError("X", "x"),
                T0.plusSeconds(1)));
    }

    @Test
    void findsUnpurgedRowsWithSourceSeasonAndTheActiveFlag() {
        PipelineRun finished = finishedRun(PipelineSource.RFETM, "2024-2025");
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "user-1", null, T0));
        artifacts.add(new RunArtifact(UUID.randomUUID(), finished.id(), ArtifactKind.ZIP, "old.zip", SHA, 1, T0));
        artifacts.add(new RunArtifact(UUID.randomUUID(), active.id(), ArtifactKind.ZIP, "new.zip", SHA, 1, T0));
        artifacts.add(new RunArtifact(UUID.randomUUID(), finished.id(), ArtifactKind.MANIFEST, "gone.json", SHA, 1,
                T0));
        artifacts.markPurged("gone.json", T0.plusSeconds(10));

        List<RetainedArtifact> found = retention.findUnpurged();

        assertThat(found).extracting(row -> row.artifact().storageKey()).containsExactlyInAnyOrder("old.zip",
                "new.zip");
        assertThat(found).filteredOn(row -> row.artifact().storageKey().equals("old.zip")).singleElement()
                .satisfies(row -> {
                    assertThat(row.source()).isEqualTo(PipelineSource.RFETM);
                    assertThat(row.season()).isEqualTo("2024-2025");
                    assertThat(row.runActive()).isFalse();
                });
        assertThat(found).filteredOn(row -> row.artifact().storageKey().equals("new.zip")).singleElement()
                .satisfies(row -> assertThat(row.runActive()).isTrue());
    }

    @Test
    void listsTheDistinctSeasonsPerSource() {
        finishedRun(PipelineSource.RFETM, "2024-2025");
        finishedRun(PipelineSource.RFETM, "2024-2025");
        finishedRun(PipelineSource.RFETM, "2025-2026");
        finishedRun(PipelineSource.FCTT, "2025-2026");

        assertThat(retention.seasonsBySource().get(PipelineSource.RFETM))
                .containsExactlyInAnyOrder("2024-2025", "2025-2026");
        assertThat(retention.seasonsBySource().get(PipelineSource.FCTT)).containsExactly("2025-2026");
        assertThat(retention.seasonsBySource()).doesNotContainKey(PipelineSource.BCNESA);
    }
}
