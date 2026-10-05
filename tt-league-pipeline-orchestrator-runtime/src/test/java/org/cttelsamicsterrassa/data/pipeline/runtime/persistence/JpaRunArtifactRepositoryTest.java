package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JpaRunArtifactRepositoryTest extends AbstractPersistenceTest {

    private static final String SHA = "0123456789abcdef".repeat(4);

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    RunArtifactRepository artifacts;

    @Test
    void roundTrips() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        RunArtifact artifact = new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, "runs/a/pack.zip", SHA,
                1234, T0);
        artifacts.add(artifact);
        RunArtifact manifest = new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.MANIFEST, "runs/a/m.json",
                SHA, 0, T0.plusSeconds(1));
        artifacts.add(manifest);

        assertThat(artifacts.findByRunId(run.id())).containsExactly(artifact, manifest);
    }

    @Test
    void duplicateKeyForSameRunAndKindIsRejected() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        artifacts.add(new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, "k.zip", SHA, 1, T0));

        assertThatThrownBy(() -> artifacts.add(
                new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, "k.zip", SHA, 1, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roundTripsPurgedAtAndFindsRowsBySharedStorageKey() {
        PipelineRun original = runs.create(queued(PipelineSource.RFETM));
        RunArtifact first = new RunArtifact(UUID.randomUUID(), original.id(), ArtifactKind.ZIP, "k.zip", SHA, 1, T0);
        artifacts.add(first);
        PipelineRun other = runs.create(queued(PipelineSource.BCNESA));
        RunArtifact shared = new RunArtifact(UUID.randomUUID(), other.id(), ArtifactKind.ZIP, "k.zip", SHA, 1,
                T0.plusSeconds(5));
        artifacts.add(shared);
        artifacts.add(new RunArtifact(UUID.randomUUID(), other.id(), ArtifactKind.MANIFEST, "other.json", SHA, 1, T0));

        assertThat(artifacts.findByStorageKey("k.zip")).containsExactly(first, shared);
        assertThat(artifacts.findByStorageKey("missing")).isEmpty();
        assertThat(artifacts.findByRunId(original.id()).get(0).purgedAt()).isNull();

        assertThat(artifacts.markPurged("k.zip", T0.plusSeconds(60))).isEqualTo(2);
        assertThat(artifacts.markPurged("k.zip", T0.plusSeconds(120))).isZero();
        assertThat(artifacts.findByStorageKey("k.zip")).allSatisfy(row -> {
            assertThat(row.isPurged()).isTrue();
            assertThat(row.purgedAt()).isEqualTo(T0.plusSeconds(60));
        });
        assertThat(artifacts.findByRunId(other.id())).filteredOn(row -> row.storageKey().equals("other.json"))
                .allMatch(row -> !row.isPurged());
    }
}
