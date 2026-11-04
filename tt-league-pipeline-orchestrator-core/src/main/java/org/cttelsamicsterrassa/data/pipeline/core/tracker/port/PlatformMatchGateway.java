package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Read-only view of the platform match states. Failures are {@code GatewayException}. */
public interface PlatformMatchGateway {

    /** Every jornada of the season, open or not. */
    PlatformRoundProgress roundProgress(PipelineSource source, String season);

    /** Every match of one competition, undated ones included. */
    PlatformCompetitionCalendar competitionCalendar(PipelineSource source, String season, String competition);
}
