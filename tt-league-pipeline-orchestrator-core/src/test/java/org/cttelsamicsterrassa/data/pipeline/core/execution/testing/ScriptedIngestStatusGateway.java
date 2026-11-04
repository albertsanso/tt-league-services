package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Returns the scripted status per source (empty when none is scripted) and counts the calls. */
public class ScriptedIngestStatusGateway implements IngestStatusGateway {

    public final List<PipelineSource> requested = new ArrayList<>();
    private final Map<PipelineSource, IngestMatchDayStatus> statuses = new EnumMap<>(PipelineSource.class);
    private RuntimeException failure;

    public void returning(IngestMatchDayStatus status) {
        statuses.put(status.source(), status);
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public Optional<IngestMatchDayStatus> matchDayStatus(PipelineSource source, String season) {
        requested.add(source);
        if (failure != null) {
            throw failure;
        }
        return Optional.ofNullable(statuses.get(source)).filter(status -> status.season().equals(season));
    }
}
