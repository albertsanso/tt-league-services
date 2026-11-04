package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for {@link ImportJob}s and their seasons.
 */
public interface ImportJobRepository {

    void save(ImportJob job);

    Optional<ImportJob> findById(UUID id);

    /**
     * The most recent job of {@code source} with {@code contentSha256} that is active ({@code QUEUED},
     * {@code STORING}, {@code IMPORTING}) or ended {@code SUCCEEDED} or {@code PARTIAL}.
     */
    Optional<ImportJob> findActiveOrSucceededBySourceAndContentSha256(ImportSource source, String contentSha256);

    /**
     * Jobs matching the optional filters, most recent first, at most {@code limit}.
     *
     * @param createdFrom inclusive lower bound of {@code createdAt}
     * @param createdBefore exclusive upper bound of {@code createdAt}
     */
    List<ImportJob> find(Optional<ImportSource> source, Optional<ZonedDateTime> createdFrom,
                         Optional<ZonedDateTime> createdBefore, int limit);

    /** Jobs in any of {@code statuses}, oldest first. */
    List<ImportJob> findByStatusIn(Set<ImportJobStatus> statuses);
}
