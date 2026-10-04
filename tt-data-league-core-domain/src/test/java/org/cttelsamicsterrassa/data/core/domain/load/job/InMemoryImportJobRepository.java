package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link ImportJobRepository} test double keeping job instances by id and counting saves. */
final class InMemoryImportJobRepository implements ImportJobRepository {
    private static final Set<ImportJobStatus> DEDUPLICATING = Set.of(ImportJobStatus.QUEUED, ImportJobStatus.STORING,
            ImportJobStatus.IMPORTING, ImportJobStatus.SUCCEEDED, ImportJobStatus.PARTIAL);

    private final Map<UUID, ImportJob> jobs = new LinkedHashMap<>();
    private int saveCount;

    int saveCount() {
        return saveCount;
    }

    List<ImportJob> all() {
        return List.copyOf(jobs.values());
    }

    @Override
    public void save(ImportJob job) {
        saveCount++;
        jobs.put(job.getId(), job);
    }

    @Override
    public Optional<ImportJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public Optional<ImportJob> findActiveOrSucceededBySourceAndContentSha256(ImportSource source,
                                                                             String contentSha256) {
        return jobs.values().stream()
                .filter(job -> job.getSource() == source)
                .filter(job -> job.getContentSha256().equals(Optional.of(contentSha256)))
                .filter(job -> DEDUPLICATING.contains(job.getStatus()))
                .max(Comparator.comparing(ImportJob::getCreatedAt));
    }

    @Override
    public List<ImportJob> find(Optional<ImportSource> source, Optional<ZonedDateTime> createdFrom,
                                Optional<ZonedDateTime> createdBefore, int limit) {
        return jobs.values().stream()
                .filter(job -> source.map(value -> job.getSource() == value).orElse(true))
                .filter(job -> createdFrom.map(from -> !job.getCreatedAt().isBefore(from)).orElse(true))
                .filter(job -> createdBefore.map(before -> job.getCreatedAt().isBefore(before)).orElse(true))
                .sorted(Comparator.comparing(ImportJob::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<ImportJob> findByStatusIn(Set<ImportJobStatus> statuses) {
        return jobs.values().stream()
                .filter(job -> statuses.contains(job.getStatus()))
                .sorted(Comparator.comparing(ImportJob::getCreatedAt))
                .toList();
    }
}
