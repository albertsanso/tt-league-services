package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollingAlerts;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.OpenMatchDays;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.PollUnitScope;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.RecentMatchDays;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuild;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuildException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Command;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;

/**
 * One adaptive polling tick for one source and the configured season. It never runs units in parallel: it launches at
 * most one run, the full refresh when it is due (it covers every unit) and otherwise one {@code GROUP} run over the
 * union of the due units. Runs go only through {@link TriggerRun}, with {@code ConflictMode.REJECT}, so a source with
 * an active run is skipped. Every write goes through {@link PollScheduleRepository#save}; a stale write aborts the tick
 * for the source and the next tick starts again from the stored state.
 */
public final class AdaptivePollingTick {

    public static final String REQUESTED_BY = "system:polling";

    /** What a tick did. */
    public enum Action {
        FULL_REFRESH_LAUNCHED,
        GROUP_LAUNCHED,
        NOTHING_DUE,
        /** The source has an active run (or a rejected trigger); every row was left untouched. */
        SKIPPED,
        /** An open match day matches no status row; an alert was raised and nothing was launched. */
        SCOPE_UNMATCHED,
        /** The ingest has no status file yet; the full refresh writes it. */
        NO_STATUS,
        /** Another writer changed a schedule; the tick was abandoned. */
        STALE
    }

    public record TickResult(Action action, UUID runId) {
    }

    private static final System.Logger LOG = System.getLogger(AdaptivePollingTick.class.getName());

    private final MatchDayRepository matchDays;
    private final IngestStatusGateway status;
    private final PollScheduleRepository schedules;
    private final PollingSettingsProvider settingsProvider;
    private final TriggerRun triggerRun;
    private final PipelineRunRepository runs;
    private final PollingAlerts alerts;
    private final ScopeBuilder scopeBuilder;
    private final RunClock clock;
    private final String season;
    private final ZoneId zone;
    private final PollingPolicy policy = new PollingPolicy();
    private final Map<PipelineSource, String> lastUnmatchedMessage = new java.util.EnumMap<>(PipelineSource.class);

    public AdaptivePollingTick(
            MatchDayRepository matchDays,
            IngestStatusGateway status,
            PollScheduleRepository schedules,
            PollingSettingsProvider settingsProvider,
            TriggerRun triggerRun,
            PipelineRunRepository runs,
            PollingAlerts alerts,
            ScopeBuilder scopeBuilder,
            RunClock clock,
            String season,
            ZoneId zone) {
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.schedules = Objects.requireNonNull(schedules, "schedules is required");
        this.settingsProvider = Objects.requireNonNull(settingsProvider, "settingsProvider is required");
        this.triggerRun = Objects.requireNonNull(triggerRun, "triggerRun is required");
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.alerts = Objects.requireNonNull(alerts, "alerts is required");
        this.scopeBuilder = Objects.requireNonNull(scopeBuilder, "scopeBuilder is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.season = PipelineRun.requireValidSeason(season);
        this.zone = Objects.requireNonNull(zone, "zone is required");
    }

    public TickResult tick(PipelineSource source) {
        Objects.requireNonNull(source, "source is required");
        try {
            return doTick(source);
        } catch (StalePollScheduleException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Adaptive polling tick for {0} abandoned, a schedule changed concurrently: {1}", source,
                    e.getMessage());
            return new TickResult(Action.STALE, null);
        }
    }

    private TickResult doTick(PipelineSource source) {
        Instant now = clock.now();
        PollingSettings settings = settingsProvider.settings(source);
        Map<String, PollSchedule> rows = new LinkedHashMap<>();
        for (PollSchedule row : schedules.findBySourceAndSeason(source, season)) {
            rows.put(row.scopeKey(), row);
        }
        applyFinishedRuns(rows);

        PollSchedule full = ensureFullRefresh(source, rows, settings, now);
        if (full.isDue(now)) {
            return launchFullRefresh(source, full, now);
        }

        List<OpenMatchDay> open = RecentMatchDays.limit(OpenMatchDays.load(matchDays, source, season),
                settings.recentMatchDays());
        ScopeBuild build;
        if (open.isEmpty()) {
            build = new ScopeBuild(List.of());
        } else {
            Optional<IngestMatchDayStatus> report = status.matchDayStatus(source, season);
            if (report.isEmpty()) {
                LOG.log(System.Logger.Level.INFO,
                        "Adaptive polling for {0}: the ingest has no match-day status yet, waiting for the full refresh",
                        source);
                return new TickResult(Action.NO_STATUS, null);
            }
            try {
                build = scopeBuilder.build(source, season, open, report.get());
            } catch (ScopeBuildException e) {
                alertUnmatched(source, full, e.getMessage(), now);
                return new TickResult(Action.SCOPE_UNMATCHED, null);
            }
        }

        List<PollSchedule> units = upsertUnits(source, rows, build, settings, now);
        List<PollSchedule> due = units.stream().filter(unit -> unit.isDue(now)).toList();
        if (due.isEmpty()) {
            return new TickResult(Action.NOTHING_DUE, null);
        }
        return launchGroup(source, due, now);
    }

    private void applyFinishedRuns(Map<String, PollSchedule> rows) {
        for (Map.Entry<String, PollSchedule> entry : rows.entrySet()) {
            PollSchedule row = entry.getValue();
            if (row.pendingRunId() == null) {
                continue;
            }
            Optional<PipelineRun> run = runs.findById(row.pendingRunId());
            PollSchedule updated;
            if (run.isEmpty()) {
                LOG.log(System.Logger.Level.WARNING, "Pending run {0} of poll schedule {1} no longer exists",
                        row.pendingRunId(), row.id());
                updated = row.pendingCleared();
            } else if (run.get().status().isTerminal()) {
                Instant finished = run.get().finishedAt() != null ? run.get().finishedAt() : clock.now();
                updated = row.outcome(run.get().status(), finished);
            } else {
                continue;
            }
            entry.setValue(schedules.save(updated));
        }
    }

    private PollSchedule ensureFullRefresh(
            PipelineSource source, Map<String, PollSchedule> rows, PollingSettings settings, Instant now) {
        PollSchedule existing = rows.get(PollSchedule.FULL_REFRESH_KEY);
        PollSchedule full;
        if (existing == null) {
            PollDecision decision = policy.fullRefresh(PollState.fresh(), settings, now);
            full = schedules.save(PollSchedule.fullRefresh(UUID.randomUUID(), source, season, decision));
        } else {
            PollSchedule decided = existing.decided(policy.fullRefresh(existing.state(), settings, now), now);
            full = decided == existing ? existing : schedules.save(decided);
        }
        rows.put(PollSchedule.FULL_REFRESH_KEY, full);
        return full;
    }

    private TickResult launchFullRefresh(PipelineSource source, PollSchedule full, Instant now) {
        if (full.pendingRunId() != null) {
            return new TickResult(Action.SKIPPED, null);
        }
        Outcome outcome = trigger(source, ScopeType.FULL_SEASON, List.of());
        if (outcome instanceof Outcome.Created created) {
            schedules.save(full.launched(created.run().id(), now));
            return new TickResult(Action.FULL_REFRESH_LAUNCHED, created.run().id());
        }
        return skipped(source, outcome);
    }

    private TickResult launchGroup(PipelineSource source, List<PollSchedule> due, Instant now) {
        List<ScopeFilter> filters = due.stream().map(PollSchedule::filter).toList();
        Outcome outcome = trigger(source, ScopeType.GROUP, filters);
        if (outcome instanceof Outcome.Created created) {
            for (PollSchedule unit : due) {
                schedules.save(unit.launched(created.run().id(), now));
            }
            return new TickResult(Action.GROUP_LAUNCHED, created.run().id());
        }
        return skipped(source, outcome);
    }

    private Outcome trigger(PipelineSource source, ScopeType type, List<ScopeFilter> filters) {
        List<Outcome> outcomes = triggerRun.trigger(new Command(List.of(source), season, type, filters, false,
                RunTrigger.SCHEDULED, REQUESTED_BY, ConflictMode.REJECT));
        if (outcomes.size() != 1) {
            throw new IllegalStateException("Expected one outcome for " + source + " but got " + outcomes.size());
        }
        return outcomes.get(0);
    }

    private TickResult skipped(PipelineSource source, Outcome outcome) {
        switch (outcome) {
            case Outcome.Rejected rejected -> LOG.log(System.Logger.Level.INFO,
                    "Adaptive polling run for {0} skipped: {1}", source, rejected.message());
            case Outcome.Queued queued -> throw new IllegalStateException(
                    "Adaptive polling for " + source + " was queued although ticks reject on conflict");
            case Outcome.Unavailable unavailable -> throw new IllegalStateException(
                    "Adaptive polling for " + source + " reported an unavailable scope: " + unavailable.code());
            case Outcome.Created created -> throw new IllegalStateException("Unexpected created run");
        }
        return new TickResult(Action.SKIPPED, null);
    }

    private List<PollSchedule> upsertUnits(
            PipelineSource source,
            Map<String, PollSchedule> rows,
            ScopeBuild build,
            PollingSettings settings,
            Instant now) {
        List<PollSchedule> units = new ArrayList<>();
        Set<String> current = new LinkedHashSet<>();
        for (PollUnitScope unit : build.units()) {
            current.add(unit.scopeKey());
            PollSchedule existing = rows.get(unit.scopeKey());
            PollSchedule next;
            if (existing == null) {
                PollDecision decision = policy.decide(PollState.fresh(), unit.candidates(), settings, now, zone);
                next = PollSchedule.group(UUID.randomUUID(), source, season, unit.scopeKey(), unit.filter(), decision,
                        now);
            } else {
                PollDecision decision = policy.decide(existing.state(), unit.candidates(), settings, now, zone);
                next = existing.withFilter(unit.filter()).decided(decision, now);
            }
            PollSchedule saved = existing == next ? existing : schedules.save(next);
            if (saved.isStopped() && saved.alertedAt() == null) {
                alerts.scopeStopped(saved);
                saved = schedules.save(saved.alerted(now));
            }
            rows.put(unit.scopeKey(), saved);
            units.add(saved);
        }
        for (PollSchedule row : List.copyOf(rows.values())) {
            if (!row.isFullRefresh() && !current.contains(row.scopeKey())) {
                schedules.delete(row.id());
                rows.remove(row.scopeKey());
            }
        }
        return units;
    }

    /** Once per distinct message and local day; the day is tracked through {@code alertedAt} of the full-refresh row. */
    private void alertUnmatched(PipelineSource source, PollSchedule full, String message, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        boolean sameDay = full.alertedAt() != null && full.alertedAt().atZone(zone).toLocalDate().equals(today);
        boolean sameMessage = message.equals(lastUnmatchedMessage.get(source));
        if (sameDay && sameMessage) {
            return;
        }
        alerts.scopeUnmatched(source, season, message);
        lastUnmatchedMessage.put(source, message);
        schedules.save(full.alerted(now));
    }
}
