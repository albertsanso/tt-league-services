package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds the stored ZIP of one unit for download; the file is read through the artifact store only. */
@Service
public class UnitPackageService {

    /** The answer: a package ready to stream, or why there is none. */
    public sealed interface Result permits Available, UnitMissing, NoPackage, Purged {
    }

    /** {@code content} must be opened (and closed) by the caller. */
    public record Available(RunUnit unit, RunArtifact artifact, ArtifactContent content) implements Result {
    }

    /** The unit does not exist or belongs to another run. */
    public record UnitMissing() implements Result {
    }

    public record NoPackage() implements Result {
    }

    /** The row is marked purged, or its file is gone from the store. */
    public record Purged() implements Result {
    }

    private final RunUnitRepository units;
    private final RunArtifactRepository artifacts;
    private final ArtifactStore store;

    UnitPackageService(RunUnitRepository units, RunArtifactRepository artifacts, ArtifactStore store) {
        this.units = units;
        this.artifacts = artifacts;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Result find(UUID runId, UUID unitId) {
        Optional<RunUnit> unit = units.findById(unitId).filter(found -> found.runId().equals(runId));
        if (unit.isEmpty()) {
            return new UnitMissing();
        }
        Optional<RunArtifact> zip = artifacts.findByUnitId(unitId).stream()
                .filter(artifact -> artifact.kind() == ArtifactKind.ZIP)
                .findFirst();
        if (zip.isEmpty()) {
            return new NoPackage();
        }
        if (zip.get().isPurged() || !store.exists(zip.get().storageKey())) {
            return new Purged();
        }
        return new Available(unit.get(), zip.get(), store.content(zip.get().storageKey()));
    }
}
