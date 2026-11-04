package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDays;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuild;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuildException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.OpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;

/**
 * Resolves the {@code OPEN_MATCH_DAYS} scope of a manual trigger from the tracker and the ingest status. It returns
 * the filters of every unit (an operator asked for the open match days, not only the due ones).
 */
public final class TrackerOpenMatchDayScopeResolver implements OpenMatchDayScopeResolver {

    public static final String NO_INGEST_STATUS = "NO_INGEST_STATUS";

    private final MatchDayRepository matchDays;
    private final IngestStatusGateway status;
    private final ScopeBuilder builder;

    public TrackerOpenMatchDayScopeResolver(
            MatchDayRepository matchDays, IngestStatusGateway status, ScopeBuilder builder) {
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.builder = Objects.requireNonNull(builder, "builder is required");
    }

    @Override
    public RunScope resolve(PipelineSource source, String season) {
        List<OpenMatchDay> open = OpenMatchDays.load(matchDays, source, season);
        if (open.isEmpty()) {
            throw new ScopeUnavailableException(ScopeUnavailableException.NO_OPEN_MATCH_DAYS,
                    "Source " + source + " has no open match days in season " + season);
        }
        Optional<IngestMatchDayStatus> report = status.matchDayStatus(source, season);
        if (report.isEmpty()) {
            throw new ScopeUnavailableException(NO_INGEST_STATUS,
                    "The ingest has no match-day status for " + source + " season " + season
                            + "; run a full-season ingest first");
        }
        ScopeBuild build;
        try {
            build = builder.build(source, season, open, report.get());
        } catch (ScopeBuildException e) {
            throw new ScopeUnavailableException(e.code(), e.getMessage());
        }
        return build.runScope();
    }
}
