package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.junit.jupiter.api.Test;

class UnitPackageServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final String SHA = "ab".repeat(32);

    private final RunUnitRepository units = mock(RunUnitRepository.class);
    private final RunArtifactRepository artifacts = mock(RunArtifactRepository.class);
    private final ArtifactStore store = mock(ArtifactStore.class);
    private final UnitPackageService service = new UnitPackageService(units, artifacts, store);

    private final UUID runId = UUID.randomUUID();
    private final RunUnit unit = RunUnit.plan(UUID.randomUUID(), runId, 0, "season", "Full season", RunScope.fullSeason());

    private RunArtifact artifact(ArtifactKind kind, String key, Instant purgedAt) {
        return new RunArtifact(UUID.randomUUID(), runId, unit.id(), kind, key, SHA, 10, T0, purgedAt);
    }

    @Test
    void anUnknownUnitOrOneOfAnotherRunIsMissing() {
        when(units.findById(unit.id())).thenReturn(Optional.empty());
        assertThat(service.find(runId, unit.id())).isInstanceOf(UnitPackageService.UnitMissing.class);

        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        assertThat(service.find(UUID.randomUUID(), unit.id())).isInstanceOf(UnitPackageService.UnitMissing.class);
        verify(artifacts, never()).findByUnitId(unit.id());
    }

    @Test
    void aUnitWithoutAZipHasNoPackage() {
        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        when(artifacts.findByUnitId(unit.id())).thenReturn(List.of(artifact(ArtifactKind.MANIFEST, "m.json", null)));

        assertThat(service.find(runId, unit.id())).isInstanceOf(UnitPackageService.NoPackage.class);

        when(artifacts.findByUnitId(unit.id())).thenReturn(List.of());
        assertThat(service.find(runId, unit.id())).isInstanceOf(UnitPackageService.NoPackage.class);
    }

    @Test
    void aPurgedRowOrAMissingFileIsPurged() {
        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        when(artifacts.findByUnitId(unit.id())).thenReturn(List.of(artifact(ArtifactKind.ZIP, "a.zip", T0.plusSeconds(1))));

        assertThat(service.find(runId, unit.id())).isInstanceOf(UnitPackageService.Purged.class);
        verify(store, never()).content("a.zip");

        when(artifacts.findByUnitId(unit.id())).thenReturn(List.of(artifact(ArtifactKind.ZIP, "b.zip", null)));
        when(store.exists("b.zip")).thenReturn(false);

        assertThat(service.find(runId, unit.id())).isInstanceOf(UnitPackageService.Purged.class);
        verify(store, never()).content("b.zip");
    }

    @Test
    void aStoredZipIsAvailableThroughTheStore() {
        RunArtifact zip = artifact(ArtifactKind.ZIP, "c.zip", null);
        ArtifactContent content = mock(ArtifactContent.class);
        when(units.findById(unit.id())).thenReturn(Optional.of(unit));
        when(artifacts.findByUnitId(unit.id())).thenReturn(List.of(artifact(ArtifactKind.MANIFEST, "m.json", null), zip));
        when(store.exists("c.zip")).thenReturn(true);
        when(store.content("c.zip")).thenReturn(content);

        UnitPackageService.Result result = service.find(runId, unit.id());

        assertThat(result).isInstanceOfSatisfying(UnitPackageService.Available.class, available -> {
            assertThat(available.unit()).isSameAs(unit);
            assertThat(available.artifact()).isSameAs(zip);
            assertThat(available.content()).isSameAs(content);
        });
    }
}
