package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunPage;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;

public interface PipelineRunRepository {

    /** Throws {@link ActiveRunConflictException} when the source already has an active run. */
    PipelineRun create(PipelineRun run);

    /**
     * Persists a transition and returns the run with the incremented version. Throws
     * {@link StaleRunException} when the stored version differs from {@code run.version()} and
     * {@link IllegalArgumentException} for an unknown id.
     */
    PipelineRun update(PipelineRun run);

    Optional<PipelineRun> findById(UUID id);

    /** The runs with the given ids that exist; unknown ids are skipped and the order is unspecified. */
    List<PipelineRun> findByIds(Collection<UUID> ids);

    Optional<PipelineRun> findActiveBySource(PipelineSource source);

    /** Newest first ({@code createdAt} descending, then {@code id}). */
    RunPage find(RunQuery query);

    /** Oldest first. */
    List<PipelineRun> findByStatusIn(Set<RunStatus> statuses);
}
