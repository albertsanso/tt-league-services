package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.util.List;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

public interface ArtifactRetentionRepository {

    List<RetainedArtifact> findUnpurged();

    /** The seasons for which each source has runs. */
    Map<PipelineSource, List<String>> seasonsBySource();
}
