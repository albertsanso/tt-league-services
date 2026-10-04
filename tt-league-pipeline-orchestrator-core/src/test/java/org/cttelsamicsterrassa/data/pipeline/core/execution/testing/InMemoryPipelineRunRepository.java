package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Enforces one active run per source and the version rules of the JPA adapter. */
public class InMemoryPipelineRunRepository implements PipelineRunRepository {

    private final Map<UUID, PipelineRun> runs = new LinkedHashMap<>();

    @Override
    public synchronized PipelineRun create(PipelineRun run) {
        if (run.status().isActive() && findActiveBySource(run.source()).isPresent()) {
            throw new ActiveRunConflictException(run.source());
        }
        if (runs.containsKey(run.id())) {
            throw new IllegalArgumentException("Run already exists: " + run.id());
        }
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public synchronized PipelineRun update(PipelineRun run) {
        PipelineRun stored = runs.get(run.id());
        if (stored == null) {
            throw new IllegalArgumentException("Unknown run: " + run.id());
        }
        if (stored.version() != run.version()) {
            throw new StaleRunException(run.id(), run.version());
        }
        PipelineRun next = PipelineRun.restore(run.id(), run.source(), run.season(), run.scope(), run.trigger(),
                run.requestedBy(), run.retryOfRunId(), run.status(), run.createdAt(), run.startedAt(),
                run.finishedAt(), run.ingestRunId(), run.importJobId(), run.error(), run.version() + 1);
        runs.put(run.id(), next);
        return next;
    }

    @Override
    public synchronized Optional<PipelineRun> findById(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public synchronized Optional<PipelineRun> findActiveBySource(PipelineSource source) {
        return runs.values().stream()
                .filter(run -> run.source() == source && run.status().isActive())
                .findFirst();
    }

    @Override
    public synchronized List<PipelineRun> findByStatusIn(Set<RunStatus> statuses) {
        List<PipelineRun> found = new ArrayList<>(
                runs.values().stream().filter(run -> statuses.contains(run.status())).toList());
        found.sort(Comparator.comparing(PipelineRun::createdAt));
        return found;
    }
}
