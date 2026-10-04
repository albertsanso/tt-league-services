package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar.PlatformCalendarMatch;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress.PlatformJornada;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Recomputes the tracked match days of one source and season from the platform round progress and calendar. All
 * reads and decisions happen first and one change set is applied at the end, so an inconsistent or failed recompute
 * writes nothing. Callers serialise recomputes; the repository version checks guard against anything else.
 */
public final class MatchDayTracker {

    private static final System.Logger LOG = System.getLogger(MatchDayTracker.class.getName());

    private final PlatformMatchGateway gateway;
    private final MatchDayRepository repository;
    private final RunClock clock;

    public MatchDayTracker(PlatformMatchGateway gateway, MatchDayRepository repository, RunClock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway is required");
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * @param run the run that just finished, or null for a periodic recompute (the result then came from an import
     *     this orchestrator did not drive)
     */
    public RecomputeOutcome recompute(PipelineSource source, String season, RunRef run) {
        Objects.requireNonNull(source, "source is required");
        Instant now = clock.now();
        Instant reportedAt = run != null ? run.finishedAt() : now;
        UUID reportedRunId = run != null ? run.runId() : null;

        PlatformRoundProgress progress = gateway.roundProgress(source, season);
        List<MatchDay> trackedDays = repository.findBySourceAndSeason(source, season);
        Map<MatchDayKey, MatchDay> trackedByKey = new HashMap<>();
        for (MatchDay day : trackedDays) {
            trackedByKey.put(day.key(), day);
        }
        Map<UUID, MatchDay> daysById = new HashMap<>();
        trackedDays.forEach(day -> daysById.put(day.id(), day));
        Map<UUID, MatchTracking> existingMatches = new HashMap<>();
        Map<UUID, List<MatchTracking>> existingByDay = new HashMap<>();
        for (MatchTracking match : repository.findMatches(daysById.keySet())) {
            existingMatches.put(match.matchId(), match);
            existingByDay.computeIfAbsent(match.matchDayId(), id -> new ArrayList<>()).add(match);
        }

        // 1. Select the jornadas to read.
        Map<MatchDayKey, PlatformJornada> selected = new LinkedHashMap<>();
        Set<MatchDayKey> listed = new HashSet<>();
                int skipped = 0;
        for (PlatformJornada jornada : progress.jornadas()) {
            if (jornada.competition() == null) {
                if (jornada.open()) {
                    skipped++;
                    LOG.log(System.Logger.Level.WARNING,
                            "Skipping open jornada without competition: {0} {1} group {2} phase {3} round {4}",
                            source, season, jornada.groupNumber(), jornada.phase(), jornada.round());
                }
                continue;
            }
            MatchDayKey key = new MatchDayKey(
                    source, season, jornada.competition(), jornada.groupNumber(), jornada.phase(), jornada.round());
            if (selected.containsKey(key) || !listed.add(key)) {
                throw new TrackerInconsistencyException("Round progress lists the jornada twice: " + key);
            }
            MatchDay tracked = trackedByKey.get(key);
            if (jornada.open() || (tracked != null && tracked.state() != MatchDayState.CLOSED)) {
                selected.put(key, jornada);
            }
        }

        // 2. Read the calendar of every selected competition once.
        Set<String> competitions = new LinkedHashSet<>();
        selected.keySet().forEach(key -> competitions.add(key.competition()));
        Map<MatchDayKey, List<PlatformCalendarMatch>> calendarByKey = new HashMap<>();
        for (String competition : competitions) {
            PlatformCompetitionCalendar calendar = gateway.competitionCalendar(source, season, competition);
            if (!calendar.today().equals(progress.today())) {
                throw new TrackerInconsistencyException("Round progress and calendar disagree on today for "
                        + competition + ": " + progress.today() + " vs " + calendar.today());
            }
            for (PlatformCalendarMatch match : calendar.matches()) {
                MatchDayKey key = new MatchDayKey(
                        source, season, competition, match.groupNumber(), match.phase(), match.round());
                if (selected.containsKey(key)) {
                    calendarByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(match);
                }
            }
        }

        // 3. Consistency check, before any change is built.
        for (Map.Entry<MatchDayKey, PlatformJornada> entry : selected.entrySet()) {
            PlatformJornada jornada = entry.getValue();
            int expected = jornada.scheduledMatches() + jornada.playedMatches();
            int actual = calendarByKey.getOrDefault(entry.getKey(), List.of()).size();
            if (expected != actual) {
                throw new TrackerInconsistencyException("Round progress reports " + expected
                        + " matches but the calendar has " + actual + " for " + entry.getKey());
            }
        }

        // 4. Build the change set.
        Changes changes = new Changes(now, run);
        Set<UUID> processed = new HashSet<>();
        Map<UUID, MatchDay> workingDays = new LinkedHashMap<>();
        Map<UUID, List<MatchTracking>> workingMatches = new LinkedHashMap<>();
        Map<UUID, MatchTracking> updatedMatches = new LinkedHashMap<>();

        for (Map.Entry<MatchDayKey, PlatformJornada> entry : selected.entrySet()) {
            MatchDayKey key = entry.getKey();
            PlatformJornada jornada = entry.getValue();
            MatchDayWindow window = jornada.firstDate() == null && jornada.lastDate() == null
                    ? MatchDayWindow.undated(progress.overdueGraceDays())
                    : new MatchDayWindow(jornada.firstDate(), jornada.lastDate(), progress.overdueGraceDays());
            MatchDay day = trackedByKey.get(key);
            if (day == null) {
                day = MatchDay.create(UUID.randomUUID(), key, window, now);
                changes.created++;
            } else {
                day = day.withWindow(window);
                changes.updated++;
            }
            workingDays.put(day.id(), day);
            List<MatchTracking> dayMatches = new ArrayList<>();
            workingMatches.put(day.id(), dayMatches);
            for (PlatformCalendarMatch calendarMatch : calendarByKey.getOrDefault(key, List.of())) {
                TrackedMatchStatus status = TrackerRules.mapCalendarState(calendarMatch.calendarState());
                MatchTracking existing = existingMatches.get(calendarMatch.id());
                MatchTracking next;
                if (existing == null) {
                    next = MatchTracking.first(calendarMatch.id(), day.id(), status, calendarMatch.dateTime(),
                            calendarMatch.homeTeamName(), calendarMatch.awayTeamName(), now, reportedAt,
                            reportedRunId);
                    changes.matchReported(day.id(), next);
                } else {
                    next = existing.observe(status, calendarMatch.dateTime(), calendarMatch.homeTeamName(),
                            calendarMatch.awayTeamName(), now, reportedAt, reportedRunId);
                    if (existing.status() != TrackedMatchStatus.REPORTED && status == TrackedMatchStatus.REPORTED) {
                        changes.matchReported(day.id(), next);
                    }
                    if (!existing.matchDayId().equals(day.id())) {
                        changes.event(existing.matchDayId(), existing.matchId(), MatchDayEventKind.MATCH_REMOVED,
                                "Moved to round " + key.round());
                        next = next.moveTo(day.id());
                    }
                }
                processed.add(next.matchId());
                dayMatches.add(next);
                updatedMatches.put(next.matchId(), next);
            }
        }

        // Matches of a selected or vanished day that the platform no longer lists in it.
        Set<UUID> removed = new LinkedHashSet<>();
        for (MatchDay day : trackedDays) {
            if (!workingDays.containsKey(day.id()) && day.state() == MatchDayState.CLOSED) {
                continue;
            }
            for (MatchTracking match : existingByDay.getOrDefault(day.id(), List.of())) {
                if (!processed.contains(match.matchId())) {
                    removed.add(match.matchId());
                    changes.event(day.id(), match.matchId(), MatchDayEventKind.MATCH_REMOVED,
                            "No longer listed by the platform");
                }
            }
        }

        // Match days that disappeared from round progress.
        for (MatchDay day : trackedDays) {
            if (!workingDays.containsKey(day.id()) && day.state() != MatchDayState.CLOSED) {
                MatchDay closed = day.close(CloseReason.REMOVED, MatchDayEvent.SYSTEM_ACTOR, now).recomputed(now);
                changes.event(day.id(), null, MatchDayEventKind.CLOSED, "Jornada no longer reported by the platform");
                changes.closed++;
                changes.days.add(closed);
            }
        }

        // Lifecycle of the selected days.
        LocalDate today = progress.today();
        for (MatchDay day : workingDays.values()) {
            List<MatchTracking> matches = workingMatches.get(day.id());
            MatchDay next = day;
            if (TrackerRules.shouldReopen(next, matches)) {
                next = next.reopen(now);
                changes.event(next.id(), null, MatchDayEventKind.REOPENED, "A match is unresolved again");
                changes.reopened++;
            } else {
                MatchDayState target = TrackerRules.targetState(next, matches, today);
                if (next.state() == MatchDayState.UPCOMING && target != MatchDayState.UPCOMING) {
                    next = next.open(now);
                    changes.event(next.id(), null, MatchDayEventKind.OPENED, null);
                    changes.opened++;
                }
                if (next.state() == MatchDayState.OPEN && target == MatchDayState.CLOSED) {
                    next = next.close(CloseReason.ALL_RESOLVED, MatchDayEvent.SYSTEM_ACTOR, now);
                    changes.event(next.id(), null, MatchDayEventKind.CLOSED, "Every match is resolved");
                    changes.closed++;
                }
            }
            changes.days.add(next.recomputed(now));
        }

        MatchDayChangeSet changeSet = new MatchDayChangeSet(
                changes.days, new ArrayList<>(updatedMatches.values()), removed, changes.events);
        if (!changeSet.isEmpty()) {
            repository.apply(changeSet);
        }
        return new RecomputeOutcome(source, season, changes.created, changes.updated, changes.opened, changes.closed,
                changes.reopened, changes.reported, skipped);
    }

    /** Accumulates days, counters and events while the change set is built. */
    private static final class Changes {

        private final Instant now;
        private final UUID runId;
        private final List<MatchDay> days = new ArrayList<>();
        private final List<MatchDayEvent> events = new ArrayList<>();
        private int created;
        private int updated;
        private int opened;
        private int closed;
        private int reopened;
        private int reported;

        private Changes(Instant now, RunRef run) {
            this.now = now;
            this.runId = run != null ? run.runId() : null;
        }

        private void event(UUID matchDayId, UUID matchId, MatchDayEventKind kind, String note) {
            events.add(MatchDayEvent.of(matchDayId, matchId, kind, MatchDayEvent.SYSTEM_ACTOR, now, runId, note));
        }

        private void matchReported(UUID matchDayId, MatchTracking match) {
            if (match.status() == TrackedMatchStatus.REPORTED) {
                reported++;
                event(matchDayId, match.matchId(), MatchDayEventKind.MATCH_REPORTED, null);
            }
        }
    }
}
