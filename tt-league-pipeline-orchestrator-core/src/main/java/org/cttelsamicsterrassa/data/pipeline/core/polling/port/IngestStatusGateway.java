package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Port to the ingest match-day status report. Kept apart from {@code IngestGateway}, which the executor uses. */
public interface IngestStatusGateway {

    /**
     * The status rows of the season, or empty when the ingest has no such file or season (HTTP 404). Other failures
     * raise {@code GatewayException}.
     */
    Optional<IngestMatchDayStatus> matchDayStatus(PipelineSource source, String season);
}
