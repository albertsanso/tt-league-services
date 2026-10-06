package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;

public interface RunUnitRepository {

    /** Inserts the planned units of one run in one atomic write; either all of them exist afterwards or none. */
    List<RunUnit> addAll(List<RunUnit> units);

    /**
     * Persists a transition or a progress change and returns the unit with the incremented version. Throws
     * {@link StaleRunException} when the stored version differs from {@code unit.version()} and
     * {@link IllegalArgumentException} for an unknown id.
     */
    RunUnit update(RunUnit unit);

    Optional<RunUnit> findById(UUID id);

    /** The units of the run ordered by ordinal; empty when the run has none. */
    List<RunUnit> findByRunId(UUID runId);

    /**
     * Up to {@code perKey} newest finished units (newest {@code finishedAt} first) of each unit key of the source,
     * skipped units excluded because they say nothing about the unit itself. A non-empty {@code unitKeys} limits the
     * keys; keys without a finished unit are absent.
     */
    Map<String, List<RunUnit>> findNewestFinishedBySource(PipelineSource source, int perKey, Set<String> unitKeys);

    /** Same per-run order as {@link #findByRunId}; runs without units are absent from the map. */
    Map<UUID, List<RunUnit>> findByRunIds(Collection<UUID> runIds);
}
