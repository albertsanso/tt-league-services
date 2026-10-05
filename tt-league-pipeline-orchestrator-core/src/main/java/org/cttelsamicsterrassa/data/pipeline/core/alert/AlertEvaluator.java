package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.ActiveAlertExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.NotificationException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notifier;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.StaleAlertException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One alert evaluation pass: reads the stored tracker and run data, asks {@link AlertRules} which conditions hold,
 * clears the alerts whose condition stopped holding, raises the new ones and sends one notification with every alert
 * that still has to go out (just raised, or whose earlier send failed). It is the only writer of alerts and never
 * calls the platform. A failed send is recorded on the alerts and retried by the next pass; gateway and repository
 * failures propagate to the caller.
 */
public final class AlertEvaluator {

    private static final System.Logger LOG = System.getLogger(AlertEvaluator.class.getName());

    private static final Set<RunStatus> TERMINAL = EnumSet.of(
            RunStatus.NO_CHANGES, RunStatus.SUCCEEDED, RunStatus.PARTIAL, RunStatus.FAILED);
    private static final Set<RunStatus> SUCCESS = EnumSet.of(RunStatus.SUCCEEDED, RunStatus.NO_CHANGES);

    private final AlertRepository alerts;
    private final MatchDayRepository matchDays;
    private final PipelineRunRepository runs;
    private final Notifier notifier;
    private final RunClock clock;
    private final AlertSettings settings;

    public AlertEvaluator(
            AlertRepository alerts,
            MatchDayRepository matchDays,
            PipelineRunRepository runs,
            Notifier notifier,
            RunClock clock,
            AlertSettings settings) {
        this.alerts = Objects.requireNonNull(alerts, "alerts is required");
        this.matchDays = Objects.requireNonNull(matchDays, "matchDays is required");
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.notifier = Objects.requireNonNull(notifier, "notifier is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.settings = Objects.requireNonNull(settings, "settings is required");
    }

    public EvaluationOutcome evaluate() {
        Instant now = clock.now();
        List<Alert> active = alerts.findActive();
        Set<AlertCondition> holding = AlertRules.holding(readFacts(now, active), settings, now);
        Map<String, AlertCondition> holdingByKey = new LinkedHashMap<>();
        holding.forEach(condition -> holdingByKey.put(key(condition.kind(), condition.conditionKey()), condition));

        int cleared = 0;
        List<Alert> toSend = new ArrayList<>();
        Map<String, Alert> stillActive = new LinkedHashMap<>();
        for (Alert alert : active) {
            String key = key(alert.kind(), alert.conditionKey());
            if (holdingByKey.containsKey(key)) {
                stillActive.put(key, alert);
                if (!alert.isNotified()) {
                    toSend.add(alert);
                }
                continue;
            }
            try {
                alerts.update(alert.cleared(now));
                cleared++;
            } catch (StaleAlertException e) {
                LOG.log(System.Logger.Level.INFO, "Alert {0} changed concurrently; the next pass repairs it",
                        alert.id());
            }
        }

        int raised = 0;
        for (AlertCondition condition : holding) {
            if (stillActive.containsKey(key(condition.kind(), condition.conditionKey()))) {
                continue;
            }
            try {
                toSend.add(alerts.raise(Alert.raise(UUID.randomUUID(), condition, now)));
                raised++;
            } catch (ActiveAlertExistsException e) {
                LOG.log(System.Logger.Level.INFO, "Alert {0} {1} was raised by another writer; skipped",
                        condition.kind(), condition.conditionKey());
            }
        }
        if (toSend.isEmpty()) {
            return new EvaluationOutcome(raised, cleared, 0, 0);
        }
        return send(toSend, now, raised, cleared);
    }

    private EvaluationOutcome send(List<Alert> toSend, Instant now, int raised, int cleared) {
        List<Alert> ordered = toSend.stream()
                .sorted(Comparator.comparing(Alert::kind).thenComparing(Alert::raisedAt))
                .toList();
        try {
            notifier.send(notification(ordered));
        } catch (NotificationException e) {
            LOG.log(System.Logger.Level.WARNING, "Alert notification of {0} alert(s) failed ({1}); will retry",
                    ordered.size(), e.getClass().getSimpleName());
            String reason = e.getClass().getSimpleName();
            for (Alert alert : ordered) {
                save(alert.notifyFailed(reason));
            }
            return new EvaluationOutcome(raised, cleared, 0, ordered.size());
        }
        for (Alert alert : ordered) {
            save(alert.notified(now));
        }
        return new EvaluationOutcome(raised, cleared, ordered.size(), 0);
    }

    private void save(Alert alert) {
        try {
            alerts.update(alert);
        } catch (StaleAlertException e) {
            LOG.log(System.Logger.Level.INFO, "Alert {0} changed concurrently; the next pass repairs it", alert.id());
        }
    }

    private static Notification notification(List<Alert> ordered) {
        String subject = ordered.size() == 1 ? ordered.get(0).title() : ordered.size() + " pipeline alerts";
        String body = ordered.stream()
                .map(alert -> alert.title() + "\n" + "-".repeat(Math.min(alert.title().length(), 72)) + "\n"
                        + alert.detail() + "\nRaised at: " + alert.raisedAt())
                .collect(Collectors.joining("\n\n"));
        return new Notification(subject, body);
    }

    private AlertFacts readFacts(Instant now, List<Alert> active) {
        List<MatchDay> open = matchDays.findByState(MatchDayState.OPEN);
        List<MatchTracking> openMatches = matchDays.findMatches(open.stream().map(MatchDay::id).toList());
        List<MatchDay> closed = matchDays.findClosedSince(now.minus(settings.closedLookback()));
        List<MatchDay> alertDays = new ArrayList<>();
        for (Alert alert : active) {
            if (alert.kind() == AlertKind.MATCH_DAY_CLOSED) {
                matchDayId(alert).flatMap(matchDays::findById).ifPresent(alertDays::add);
            }
        }
        Map<PipelineSource, List<PipelineRun>> newestTerminal = new EnumMap<>(PipelineSource.class);
        Map<PipelineSource, Instant> newestSuccess = new EnumMap<>(PipelineSource.class);
        for (PipelineSource source : PipelineSource.values()) {
            newestTerminal.put(source, runs.find(new RunQuery(Set.of(source), TERMINAL, null, null, 0, 2)).items());
            runs.find(new RunQuery(Set.of(source), SUCCESS, null, null, 0, 1)).items().stream()
                    .findFirst()
                    .ifPresent(run -> newestSuccess.put(source, run.finishedAt()));
        }
        return new AlertFacts(open, closed, openMatches, alertDays, newestTerminal, newestSuccess);
    }

    private static Optional<UUID> matchDayId(Alert alert) {
        try {
            return Optional.of(UUID.fromString(alert.conditionKey()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String key(AlertKind kind, String conditionKey) {
        return kind + "/" + conditionKey;
    }
}
