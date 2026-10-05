package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Manual operator actions on a match day. Each one loads the day, applies the aggregate transition, appends an event
 * with the actor and time, and saves everything in one change set. Ignoring the last unresolved match closes an
 * open day as ALL_RESOLVED (a second, system event), and unignoring a match of such a day reopens it. Reopening
 * does not re-run the auto-close rule: a reopened day whose matches are all resolved closes again on the next
 * recompute.
 */
public final class MatchDayActions {

    private final MatchDayRepository repository;
    private final RunClock clock;

    public MatchDayActions(MatchDayRepository repository, RunClock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public void close(UUID matchDayId, String actor, String note) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        MatchDay closed = day.close(CloseReason.MANUAL, actor, now);
        save(closed, List.of(), List.of(event(matchDayId, null, MatchDayEventKind.CLOSED, actor, now, note)));
    }

    public void reopen(UUID matchDayId, String actor, String note) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        MatchDay reopened = day.reopen(now);
        save(reopened, List.of(), List.of(event(matchDayId, null, MatchDayEventKind.REOPENED, actor, now, note)));
    }

    public void ignoreMatch(UUID matchDayId, UUID matchId, String actor, String note) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        List<MatchTracking> matches = repository.findMatches(Set.of(matchDayId));
        MatchTracking match = find(matches, matchDayId, matchId);
        MatchTracking ignored = match.ignore(actor, now);
        List<MatchDayEvent> events = new ArrayList<>();
        events.add(event(matchDayId, matchId, MatchDayEventKind.MATCH_IGNORED, actor, now, note));
        MatchDay next = day;
        if (day.state() == MatchDayState.OPEN && TrackerRules.canAutoClose(replace(matches, ignored))) {
            next = day.close(CloseReason.ALL_RESOLVED, MatchDayEvent.SYSTEM_ACTOR, now);
            events.add(event(matchDayId, null, MatchDayEventKind.CLOSED, MatchDayEvent.SYSTEM_ACTOR, now,
                    "Every match is resolved"));
        }
        save(next, List.of(ignored), events);
    }

    public void unignoreMatch(UUID matchDayId, UUID matchId, String actor, String note) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        List<MatchTracking> matches = repository.findMatches(Set.of(matchDayId));
        MatchTracking match = find(matches, matchDayId, matchId);
        MatchTracking unignored = match.unignore();
        List<MatchDayEvent> events = new ArrayList<>();
        events.add(event(matchDayId, matchId, MatchDayEventKind.MATCH_UNIGNORED, actor, now, note));
        MatchDay next = day;
        if (TrackerRules.shouldReopen(day, replace(matches, unignored))) {
            next = day.reopen(now);
            events.add(event(matchDayId, null, MatchDayEventKind.REOPENED, MatchDayEvent.SYSTEM_ACTOR, now,
                    "A match is unresolved again"));
        }
        save(next, List.of(unignored), events);
    }

    /** {@code matchId} is null for a note on the match day itself. */
    public void addNote(UUID matchDayId, UUID matchId, String actor, String text) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        if (matchId != null) {
            find(repository.findMatches(Set.of(matchDayId)), matchDayId, matchId);
        }
        save(day, List.of(), List.of(event(matchDayId, matchId, MatchDayEventKind.NOTE, actor, now, text)));
    }

    /** Records that an operator launched a refresh run from the match day; no state change, any state allowed. */
    public void recordRefresh(UUID matchDayId, String actor, UUID runId, String note) {
        Instant now = clock.now();
        MatchDay day = load(matchDayId);
        save(day, List.of(), List.of(MatchDayEvent.of(
                matchDayId, null, MatchDayEventKind.REFRESH_REQUESTED, actor, now, runId, note)));
    }

    private MatchDay load(UUID matchDayId) {
        return repository.findById(matchDayId).orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
    }

    private static MatchTracking find(List<MatchTracking> matches, UUID matchDayId, UUID matchId) {
        return matches.stream()
                .filter(match -> match.matchId().equals(matchId))
                .findFirst()
                .orElseThrow(() -> new MatchDayNotFoundException(matchDayId, matchId));
    }

    private static List<MatchTracking> replace(List<MatchTracking> matches, MatchTracking changed) {
        return matches.stream().map(match -> match.matchId().equals(changed.matchId()) ? changed : match).toList();
    }

    private static MatchDayEvent event(
            UUID matchDayId, UUID matchId, MatchDayEventKind kind, String actor, Instant now, String note) {
        return MatchDayEvent.of(matchDayId, matchId, kind, actor, now, null, note);
    }

    private void save(MatchDay day, List<MatchTracking> matches, List<MatchDayEvent> events) {
        repository.apply(new MatchDayChangeSet(List.of(day), matches, Set.of(), events));
    }
}
