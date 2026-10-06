package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuild;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuildException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Command;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;

/**
 * Re-ingests one match day on an operator's request: builds the ingest filters of that match day's round of its
 * group with the {@link ScopeBuilder} (the same vocabulary as {@code OPEN_MATCH_DAYS}; RFETM scopes carry only the
 * category, so the whole category is refreshed) and creates the run through {@link TriggerRun}. It works for any
 * match-day state. When the round has no status row yet, the group's units limited to that round are used. A created or queued run is recorded as a {@code REFRESH_REQUESTED} event on the match day.
 */
public final class MatchDayRefresh {

    private final MatchDayRepository matchDays;
    private final IngestStatusGateway status;
    private final ScopeBuilder builder;
    private final TriggerRun triggerRun;
    private final MatchDayActions actions;

    public MatchDayRefresh(
            MatchDayRepository matchDays,
            IngestStatusGateway status,
            ScopeBuilder builder,
            TriggerRun triggerRun,
            MatchDayActions actions) {
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.builder = Objects.requireNonNull(builder, "builder is required");
        this.triggerRun = Objects.requireNonNull(triggerRun, "triggerRun is required");
        this.actions = Objects.requireNonNull(actions, "actions is required");
    }

    public List<Outcome> refresh(UUID matchDayId, boolean force, String actor, ConflictMode conflictMode) {
        MatchDay day = matchDays.findById(matchDayId).orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        List<MatchTracking> matches = matchDays.findMatches(List.of(matchDayId)).stream()
                .filter(match -> !match.isIgnored())
                .toList();
        Optional<IngestMatchDayStatus> report = status.matchDayStatus(day.key().source(), day.key().season());
        if (report.isEmpty()) {
            return List.of(new Outcome.Unavailable(day.key().source(),
                    TrackerOpenMatchDayScopeResolver.NO_INGEST_STATUS,
                    "The ingest has no match-day status for " + day.key().source() + " season "
                            + day.key().season() + "; run a full-season ingest first"));
        }
        OpenMatchDay open = new OpenMatchDay(day.key(), matches);
        ScopeBuild build;
        try {
            build = buildScope(day, open, report.get());
        } catch (ScopeBuildException e) {
            return List.of(new Outcome.Unavailable(day.key().source(), e.code(), e.getMessage()));
        }
        List<Outcome> outcomes = triggerRun.trigger(new Command(List.of(day.key().source()), day.key().season(),
                ScopeType.GROUP, build.runScope().filters(), force, RunTrigger.MANUAL, actor, conflictMode));
        for (Outcome outcome : outcomes) {
            if (outcome instanceof Outcome.Created created) {
                actions.recordRefresh(matchDayId, actor, created.run().id(), null);
            } else if (outcome instanceof Outcome.Queued queued) {
                actions.recordRefresh(matchDayId, actor, null, "Queued behind active run " + queued.activeRunId());
            }
        }
        return outcomes;
    }

    /**
     * The scope of the match day's own status row; when its round has none (page not downloaded yet), the group's
     * units limited to that round, so the refresh can fetch the missing page.
     */
    private ScopeBuild buildScope(MatchDay day, OpenMatchDay open, IngestMatchDayStatus report) {
        try {
            return builder.build(day.key().source(), day.key().season(), List.of(open), report);
        } catch (ScopeBuildException e) {
            if (!ScopeBuildException.SCOPE_UNMATCHED.equals(e.code())) {
                throw e;
            }
            return builder.buildGroupRound(day.key().source(), day.key().season(), open, report);
        }
    }
}
