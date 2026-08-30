package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJob;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobRequest;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportSourcesPort;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportJobsPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryImportJobsService implements ImportJobsPort {
    private static final String MAPPING_VERSION = "1";
    private final Map<UUID, ImportJob> jobs = new ConcurrentHashMap<>();
    private final Set<ImportSource> allowedSources;

    public InMemoryImportJobsService() {
        this(() -> List.of(ImportSource.values()));
    }

    @Autowired
    public InMemoryImportJobsService(ImportSourcesPort importSourcesPort) {
        if (importSourcesPort == null || importSourcesPort.listSupportedSources() == null) {
            throw new IllegalArgumentException("supported import sources are required");
        }
        this.allowedSources = Set.copyOf(importSourcesPort.listSupportedSources());
    }

    @Override
    public ImportJob preview(ImportJobRequest request) {
        if (request == null || request.source() == null) throw new IllegalArgumentException("source is required");
        if (!allowedSources.contains(request.source())) throw new IllegalArgumentException("source is not enabled");
        String mapping = request.mappingVersion() == null ? MAPPING_VERSION : request.mappingVersion().trim();
        if (!MAPPING_VERSION.equals(mapping)) throw new IllegalArgumentException("unsupported mapping version");
        Instant now = Instant.now();
        ImportJob job = new ImportJob(UUID.randomUUID(), request.source(), mapping,
                request.preview() ? ImportJobStatus.PREVIEW : ImportJobStatus.VALIDATING,
                0, 0, 0, 0, now, now);
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public ImportJob validate(UUID id) {
        return transition(id, job -> {
            if (job.status() != ImportJobStatus.PREVIEW && job.status() != ImportJobStatus.VALIDATING) {
                throw new IllegalStateException("job cannot be validated");
            }
            return replace(job, ImportJobStatus.READY, job.processed(), job.total());
        });
    }

    @Override
    public ImportJob start(UUID id) {
        return transition(id, job -> {
            if (job.status() != ImportJobStatus.READY) throw new IllegalStateException("job is not ready");
            return replace(job, ImportJobStatus.SUCCEEDED, 1, 1);
        });
    }

    @Override
    public ImportJob cancel(UUID id) {
        return transition(id, job -> {
            if (job.status() == ImportJobStatus.SUCCEEDED || job.status() == ImportJobStatus.ROLLED_BACK
                    || job.status() == ImportJobStatus.CANCELLED) return job;
            return replace(job, ImportJobStatus.CANCELLED, job.processed(), job.total());
        });
    }

    @Override
    public ImportJob rollback(UUID id) {
        return transition(id, job -> {
            if (job.status() == ImportJobStatus.ROLLED_BACK) return job;
            if (job.status() != ImportJobStatus.SUCCEEDED) {
                throw new IllegalStateException("only succeeded jobs can be rolled back");
            }
            return replace(job, ImportJobStatus.ROLLED_BACK, job.processed(), job.total());
        });
    }

    @Override public Optional<ImportJob> find(UUID id) { return Optional.ofNullable(jobs.get(id)); }

    @Override public List<ImportJob> history(String query, int limit) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int bounded = Math.max(1, Math.min(limit, 100));
        return jobs.values().stream()
                .filter(j -> term.isEmpty() || j.source().name().toLowerCase(Locale.ROOT).contains(term)
                        || j.status().name().toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(ImportJob::createdAt).reversed()).limit(bounded).toList();
    }

    private ImportJob transition(UUID id, java.util.function.UnaryOperator<ImportJob> transition) {
        if (id == null) throw new NoSuchElementException("import job not found");
        final ImportJob[] result = new ImportJob[1];
        jobs.compute(id, (jobId, current) -> {
            if (current == null) throw new NoSuchElementException("import job not found");
            result[0] = transition.apply(current);
            return result[0];
        });
        return result[0];
    }
    private ImportJob replace(ImportJob old, ImportJobStatus status, int processed, int total) {
        ImportJob next = new ImportJob(old.id(), old.source(), old.mappingVersion(), status, processed, total,
                old.errors(), old.warnings(), old.createdAt(), Instant.now());
        jobs.put(old.id(), next);
        return next;
    }
}
