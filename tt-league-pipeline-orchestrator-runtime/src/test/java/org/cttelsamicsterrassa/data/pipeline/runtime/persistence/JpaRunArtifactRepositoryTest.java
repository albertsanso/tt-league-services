package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
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
        RunUnit unit = unit(run);
        RunArtifact artifact = new RunArtifact(UUID.randomUUID(), run.id(), unit.id(), ArtifactKind.ZIP,
                "runs/a/pack.zip", SHA, 1234, T0);
        artifacts.add(artifact);
        RunArtifact manifest = new RunArtifact(UUID.randomUUID(), run.id(), unit.id(), ArtifactKind.MANIFEST,
                "runs/a/m.json", SHA, 0, T0.plusSeconds(1));
        artifacts.add(manifest);

        assertThat(artifacts.findByRunId(run.id())).containsExactly(artifact, manifest);
        assertThat(artifacts.findByUnitId(unit.id())).containsExactly(artifact, manifest);
        assertThat(artifacts.findByUnitId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsTheArtifactsOfEachUnitOfARun() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        java.util.List<RunUnit> units = units(run,
                new org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter("A", null, null, null, null,
                        java.util.List.of(1)),
                new org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter("B", null, null, null, null,
                        java.util.List.of(1)));
        RunArtifact first = artifacts.add(new RunArtifact(UUID.randomUUID(), run.id(), units.get(0).id(),
                ArtifactKind.ZIP, "u0/pack.zip", SHA, 1, T0));
        RunArtifact second = artifacts.add(new RunArtifact(UUID.randomUUID(), run.id(), units.get(1).id(),
                ArtifactKind.ZIP, "u1/pack.zip", SHA, 1, T0));

        assertThat(artifacts.findByUnitId(units.get(0).id())).containsExactly(first);
        assertThat(artifacts.findByUnitId(units.get(1).id())).containsExactly(second);
        assertThat(artifacts.findByRunId(run.id())).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void duplicateKeyForSameRunAndKindIsRejected() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        RunUnit unit = unit(run);
        artifacts.add(new RunArtifact(UUID.randomUUID(), run.id(), unit.id(), ArtifactKind.ZIP, "k.zip", SHA, 1, T0));

        assertThatThrownBy(() -> artifacts.add(
                new RunArtifact(UUID.randomUUID(), run.id(), unit.id(), ArtifactKind.ZIP, "k.zip", SHA, 1, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anArtifactNeedsAnExistingUnit() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));

        assertThatThrownBy(() -> artifacts.add(new RunArtifact(UUID.randomUUID(), run.id(), UUID.randomUUID(),
                ArtifactKind.ZIP, "k.zip", SHA, 1, T0))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roundTripsPurgedAtAndFindsRowsBySharedStorageKey() {
        PipelineRun original = runs.create(queued(PipelineSource.RFETM));
        RunUnit originalUnit = unit(original);
        RunArtifact first = new RunArtifact(UUID.randomUUID(), original.id(), originalUnit.id(), ArtifactKind.ZIP,
                "k.zip", SHA, 1, T0);
        artifacts.add(first);
        PipelineRun other = runs.create(queued(PipelineSource.BCNESA));
        RunUnit otherUnit = unit(other);
        RunArtifact shared = new RunArtifact(UUID.randomUUID(), other.id(), otherUnit.id(), ArtifactKind.ZIP, "k.zip",
                SHA, 1, T0.plusSeconds(5));
        artifacts.add(shared);
        artifacts.add(new RunArtifact(UUID.randomUUID(), other.id(), otherUnit.id(), ArtifactKind.MANIFEST,
                "other.json", SHA, 1, T0));

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
