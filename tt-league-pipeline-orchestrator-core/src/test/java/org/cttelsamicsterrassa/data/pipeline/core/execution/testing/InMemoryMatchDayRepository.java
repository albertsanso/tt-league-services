package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayFacets;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDaySummary;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Version-checked, all-or-nothing in-memory tracker store. */
public class InMemoryMatchDayRepository implements MatchDayRepository {

    private final Map<UUID, MatchDay> days = new LinkedHashMap<>();
    private final Map<UUID, MatchTracking> matches = new LinkedHashMap<>();
    private final List<MatchDayEvent> events = new ArrayList<>();
    private int applyCalls;

    public synchronized int applyCalls() {
        return applyCalls;
    }

    public synchronized List<MatchDay> allDays() {
        return List.copyOf(days.values());
    }

    public synchronized List<MatchTracking> allMatches() {
        return List.copyOf(matches.values());
    }

    public synchronized List<MatchDayEvent> allEvents() {
        return List.copyOf(events);
    }

    public synchronized Optional<MatchDay> dayOf(MatchDayKey key) {
        return days.values().stream().filter(day -> day.key().equals(key)).findFirst();
    }

    @Override
    public synchronized Optional<MatchDay> findById(UUID matchDayId) {
        return Optional.ofNullable(days.get(matchDayId));
    }

    @Override
    public synchronized List<MatchDay> findBySourceAndSeason(PipelineSource source, String season) {
        return days.values().stream()
                .filter(day -> day.key().source() == source && day.key().season().equals(season))
                .toList();
    }

    @Override
    public synchronized List<MatchTracking> findMatches(Collection<UUID> matchDayIds) {
        return matches.values().stream().filter(match -> matchDayIds.contains(match.matchDayId())).toList();
    }

    @Override
    public synchronized Optional<MatchTracking> findMatch(UUID matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    @Override
    public synchronized List<MatchDayEvent> findEvents(UUID matchDayId) {
        return events.stream()
                .filter(event -> event.matchDayId().equals(matchDayId))
                .sorted(Comparator.comparing(MatchDayEvent::occurredAt))
                .toList();
    }

    @Override
    public synchronized Set<SourceSeason> findSourceSeasonsWithUnclosedDays() {
        Set<SourceSeason> result = new LinkedHashSet<>();
        for (MatchDay day : days.values()) {
            if (day.state() != MatchDayState.CLOSED) {
                result.add(new SourceSeason(day.key().source(), day.key().season()));
            }
        }
        return result;
    }

    @Override
    public synchronized List<MatchDay> findByState(MatchDayState state) {
        return days.values().stream().filter(day -> day.state() == state).toList();
    }

    @Override
    public synchronized List<MatchDay> findClosedSince(Instant since) {
        return days.values().stream()
                .filter(day -> day.state() == MatchDayState.CLOSED && !day.closedAt().isBefore(since))
                .toList();
    }

    @Override
    public synchronized MatchDayPage query(MatchDayQuery query) {
        Comparator<MatchDay> order = Comparator
                .comparing((MatchDay day) -> day.window().firstDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(day -> day.key().competition())
                .thenComparing(day -> day.key().groupNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(day -> day.key().phase(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(day -> day.key().round());
        List<MatchDay> filtered = days.values().stream()
                .filter(day -> query.source() == null || day.key().source() == query.source())
                .filter(day -> query.season() == null || day.key().season().equals(query.season()))
                .filter(day -> query.state() == null || day.state() == query.state())
                .filter(day -> query.competition() == null || day.key().competition().equals(query.competition()))
                .filter(day -> query.phase() == null || query.phase().equals(day.key().phase()))
                .filter(day -> !query.undated() || !day.window().isDated())
                .filter(day -> overlaps(day, query.from(), query.to()))
                .sorted(order)
                .toList();
        int from = Math.min(query.page() * query.size(), filtered.size());
        int to = Math.min(from + query.size(), filtered.size());
        List<MatchDaySummary> items = filtered.subList(from, to).stream().map(this::summary).toList();
        return new MatchDayPage(items, filtered.size(), query.page(), query.size());
    }

    private static boolean overlaps(MatchDay day, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (!day.window().isDated()) {
            return false;
        }
        boolean afterFrom = from == null || !day.window().lastDate().isBefore(from);
        boolean beforeTo = to == null || !day.window().firstDate().isAfter(to);
        return afterFrom && beforeTo;
    }

    private MatchDaySummary summary(MatchDay day) {
        Map<TrackedMatchStatus, Integer> counts = new EnumMap<>(TrackedMatchStatus.class);
        Map<TrackedMatchStatus, Integer> ignored = new EnumMap<>(TrackedMatchStatus.class);
        for (MatchTracking match : matches.values()) {
            if (match.matchDayId().equals(day.id())) {
                counts.merge(match.status(), 1, Integer::sum);
                if (match.isIgnored()) {
                    ignored.merge(match.status(), 1, Integer::sum);
                }
            }
        }
        return new MatchDaySummary(day, counts, ignored);
    }

    @Override
    public synchronized MatchDayFacets facets(PipelineSource source, String season) {
        Set<String> seasons = new TreeSet<>();
        Set<String> competitions = new TreeSet<>();
        Set<String> phases = new TreeSet<>();
        for (MatchDay day : days.values()) {
            if (source != null && day.key().source() != source) {
                continue;
            }
            seasons.add(day.key().season());
            if (season == null || day.key().season().equals(season)) {
                competitions.add(day.key().competition());
                if (day.key().phase() != null) {
                    phases.add(day.key().phase());
                }
            }
        }
        return new MatchDayFacets(List.copyOf(seasons), List.copyOf(competitions), List.copyOf(phases));
    }

    @Override
    public synchronized void apply(MatchDayChangeSet changes) {
        applyCalls++;
        Map<UUID, MatchDay> nextDays = new LinkedHashMap<>(days);
        Map<UUID, MatchTracking> nextMatches = new LinkedHashMap<>(matches);
        Map<MatchDayKey, UUID> keys = new HashMap<>();
        nextDays.values().forEach(day -> keys.put(day.key(), day.id()));
        for (MatchDay day : changes.days()) {
            MatchDay stored = nextDays.get(day.id());
            if (stored == null) {
                UUID other = keys.get(day.key());
                if (other != null) {
                    throw new StaleMatchDayException("Match day key already exists: " + day.key());
                }
                keys.put(day.key(), day.id());
                nextDays.put(day.id(), day);
            } else {
                if (stored.version() != day.version()) {
                    throw new StaleMatchDayException("Match day " + day.id() + " changed concurrently");
                }
                nextDays.put(day.id(), withVersion(day, day.version() + 1));
            }
        }
        for (MatchTracking match : changes.matches()) {
            MatchTracking stored = nextMatches.get(match.matchId());
            if (stored == null) {
                nextMatches.put(match.matchId(), match);
            } else {
                if (stored.version() != match.version()) {
                    throw new StaleMatchDayException("Match " + match.matchId() + " changed concurrently");
                }
                nextMatches.put(match.matchId(), withVersion(match, match.version() + 1));
            }
            if (!nextDays.containsKey(match.matchDayId())) {
                throw new IllegalStateException("Match refers to an unknown match day " + match.matchDayId());
            }
        }
        changes.removedMatchIds().forEach(nextMatches::remove);
        days.clear();
        days.putAll(nextDays);
        matches.clear();
        matches.putAll(nextMatches);
        events.addAll(changes.events());
    }

    private static MatchDay withVersion(MatchDay day, long version) {
        return MatchDay.restore(day.id(), day.key(), day.window(), day.state(), day.closeReason(), day.closedAt(),
                day.closedBy(), day.openedAt(), day.createdAt(), day.lastRecomputedAt(), version);
    }

    private static MatchTracking withVersion(MatchTracking match, long version) {
        return MatchTracking.restore(match.matchId(), match.matchDayId(), match.status(), match.matchDateTime(),
                match.homeTeamName(), match.awayTeamName(), match.firstSeenAt(), match.statusChangedAt(),
                match.lastSeenAt(), match.reportedAt(), match.reportedRunId(), match.ignoredAt(), match.ignoredBy(),
                version);
    }
}
