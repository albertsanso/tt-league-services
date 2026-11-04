package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.retention.RetainedArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;

/**
 * Reads the unpurged rows of an {@link RunArtifactRepository} and lets tests state each row's source, season and
 * whether its run is active.
 */
public class InMemoryArtifactRetentionRepository implements ArtifactRetentionRepository {

    private record Context(PipelineSource source, String season, boolean active) {
    }

    private final InMemoryRunArtifactRepository artifactRows;
    private final Map<java.util.UUID, Context> contexts = new java.util.HashMap<>();
    private final Map<PipelineSource, List<String>> seasons = new EnumMap<>(PipelineSource.class);

    public InMemoryArtifactRetentionRepository(InMemoryRunArtifactRepository artifactRows) {
        this.artifactRows = artifactRows;
    }

    /** Declares the source, season and active flag of a run; its season also counts for {@code seasonsBySource}. */
    public synchronized InMemoryArtifactRetentionRepository run(
            java.util.UUID runId, PipelineSource source, String season, boolean active) {
        contexts.put(runId, new Context(source, season, active));
        List<String> known = seasons.computeIfAbsent(source, key -> new ArrayList<>());
        if (!known.contains(season)) {
            known.add(season);
        }
        return this;
    }

    @Override
    public synchronized List<RetainedArtifact> findUnpurged() {
        List<RetainedArtifact> result = new ArrayList<>();
        for (RunArtifact artifact : artifactRows.all()) {
            Context context = contexts.get(artifact.runId());
            if (context != null && !artifact.isPurged()) {
                result.add(new RetainedArtifact(artifact, context.source(), context.season(), context.active()));
            }
        }
        return result;
    }

    @Override
    public synchronized Map<PipelineSource, List<String>> seasonsBySource() {
        Map<PipelineSource, List<String>> copy = new EnumMap<>(PipelineSource.class);
        seasons.forEach((source, list) -> copy.put(source, List.copyOf(list)));
        return copy;
    }
}
