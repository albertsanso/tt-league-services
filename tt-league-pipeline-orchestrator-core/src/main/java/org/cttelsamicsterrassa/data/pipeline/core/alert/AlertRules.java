package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The only place that decides which alert conditions hold. A pure function: no I/O and no clock. A condition is
 * identified by its kind and key; the evaluator raises the ones that hold and clears the ones that stopped holding.
 */
public final class AlertRules {

    private static final Set<TrackedMatchStatus> AWAITED =
            EnumSet.of(TrackedMatchStatus.SCHEDULED, TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.OVERDUE);

    private AlertRules() {
    }

    public static Set<AlertCondition> holding(AlertFacts facts, AlertSettings settings, Instant now) {
        Objects.requireNonNull(facts, "facts is required");
        Objects.requireNonNull(settings, "settings is required");
        Objects.requireNonNull(now, "now is required");
        Set<AlertCondition> holding = new LinkedHashSet<>();
        closedDays(facts, settings, now, holding);
        runFailures(facts, holding);
        unreportedMatches(facts, settings, now, holding);
        noRecentSuccess(facts, settings, now, holding);
        return holding;
    }

    private static void closedDays(AlertFacts facts, AlertSettings settings, Instant now, Set<AlertCondition> out) {
        Instant since = now.minus(settings.closedLookback());
        Map<UUID, MatchDay> closed = new LinkedHashMap<>();
        for (MatchDay day : facts.recentlyClosedDays()) {
            if (day.state() == MatchDayState.CLOSED && !day.closedAt().isBefore(since)) {
                closed.put(day.id(), day);
            }
        }
        for (MatchDay day : facts.activeAlertDays()) {
            if (day.state() == MatchDayState.CLOSED) {
                closed.putIfAbsent(day.id(), day);
            }
        }
        for (MatchDay day : closed.values()) {
            out.add(new AlertCondition(AlertKind.MATCH_DAY_CLOSED, day.id().toString(), day.key().source(),
                    day.key().season(), AlertTexts.closedTitle(day), AlertTexts.closedDetail(day)));
        }
    }

    private static void runFailures(AlertFacts facts, Set<AlertCondition> out) {
        for (PipelineSource source : PipelineSource.values()) {
            List<PipelineRun> runs = facts.newestTerminalRuns().getOrDefault(source, List.of());
            if (runs.size() >= 2
                    && runs.get(0).status() == RunStatus.FAILED
                    && runs.get(1).status() == RunStatus.FAILED) {
                out.add(new AlertCondition(AlertKind.RUN_FAILURES, source.name(), source, null,
                        AlertTexts.failuresTitle(source),
                        AlertTexts.failuresDetail(source, List.of(runs.get(0), runs.get(1)))));
            }
        }
    }

    private static void unreportedMatches(
            AlertFacts facts, AlertSettings settings, Instant now, Set<AlertCondition> out) {
        Map<UUID, MatchDay> openById = new HashMap<>();
        facts.openDays().forEach(day -> openById.put(day.id(), day));
        Instant limit = now.minus(settings.unreportedAfter());
        for (MatchTracking match : facts.openMatches()) {
            MatchDay day = openById.get(match.matchDayId());
            if (day == null || day.state() != MatchDayState.OPEN || match.isIgnored()
                    || !AWAITED.contains(match.status()) || match.matchDateTime() == null
                    || match.matchDateTime().isAfter(limit)) {
                continue;
            }
            out.add(new AlertCondition(AlertKind.MATCH_UNREPORTED, match.matchId().toString(), day.key().source(),
                    day.key().season(), AlertTexts.unreportedTitle(day, match),
                    AlertTexts.unreportedDetail(day, match)));
        }
    }

    private static void noRecentSuccess(
            AlertFacts facts, AlertSettings settings, Instant now, Set<AlertCondition> out) {
        Instant limit = now.minus(settings.noSuccessWindow());
        for (PipelineSource source : PipelineSource.values()) {
            List<MatchDay> open = facts.openDays().stream()
                    .filter(day -> day.state() == MatchDayState.OPEN && day.key().source() == source)
                    .toList();
            if (open.isEmpty()) {
                continue;
            }
            Instant earliestOpened = open.stream()
                    .map(MatchDay::openedAt)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            Instant newestSuccess = facts.newestSuccess().get(source);
            Instant reference = newestSuccess != null && newestSuccess.isAfter(earliestOpened)
                    ? newestSuccess
                    : earliestOpened;
            if (!reference.isAfter(limit)) {
                out.add(new AlertCondition(AlertKind.NO_RECENT_SUCCESS, source.name(), source, null,
                        AlertTexts.noSuccessTitle(source),
                        AlertTexts.noSuccessDetail(source, reference, newestSuccess, open.size())));
            }
        }
    }
}
