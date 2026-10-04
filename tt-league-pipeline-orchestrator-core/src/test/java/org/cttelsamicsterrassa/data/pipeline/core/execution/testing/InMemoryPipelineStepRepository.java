package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InMemoryPipelineStepRepository implements PipelineStepRepository {

    private final Map<UUID, PipelineStep> steps = new LinkedHashMap<>();

    @Override
    public synchronized PipelineStep save(PipelineStep step) {
        for (PipelineStep other : steps.values()) {
            if (!other.id().equals(step.id()) && other.runId().equals(step.runId()) && other.kind() == step.kind()
                    && other.attempt() == step.attempt()) {
                throw new IllegalArgumentException(
                        "Duplicate step " + step.kind() + "/" + step.attempt() + " for run " + step.runId());
            }
        }
        steps.put(step.id(), step);
        return step;
    }

    @Override
    public synchronized Map<UUID, List<PipelineStep>> findByRunIds(Collection<UUID> runIds) {
        Map<UUID, List<PipelineStep>> byRun = new LinkedHashMap<>();
        for (UUID runId : runIds) {
            List<PipelineStep> found = findByRunId(runId);
            if (!found.isEmpty()) {
                byRun.put(runId, found);
            }
        }
        return byRun;
    }

    @Override
    public synchronized List<PipelineStep> findByRunId(UUID runId) {
        return steps.values().stream()
                .filter(step -> step.runId().equals(runId))
                .sorted(Comparator.comparing(PipelineStep::startedAt).thenComparingInt(PipelineStep::attempt))
                .toList();
    }
}
