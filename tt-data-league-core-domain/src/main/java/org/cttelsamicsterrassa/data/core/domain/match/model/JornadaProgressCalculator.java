package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Composes the group headers of {@link RoundProgressCalculator} with the per-match states of
 * {@link CalendarStateResolver} into per-jornada progress (FEAT-00102). It re-implements neither rule.
 *
 * <p>Manual overdue marks only count for {@link MatchStatus#SCHEDULED} entries. Results are sorted by
 * competition, group number, phase (nulls last) and round.</p>
 */
public final class JornadaProgressCalculator {

    private JornadaProgressCalculator() {
    }

    /**
     * @throws IllegalStateException if an entry belongs to a group missing from {@code progress}
     */
    public static List<JornadaProgress> compute(List<RoundProgress> progress,
                                                Collection<MatchCalendarEntry> entries,
                                                Set<UUID> overdueMarkedMatchIds,
                                                LocalDate today, OverdueGracePeriod grace) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(overdueMarkedMatchIds, "overdueMarkedMatchIds");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(grace, "grace");

        Map<GroupKey, Integer> currentRoundByGroup = new LinkedHashMap<>();
        for (RoundProgress row : progress) {
            currentRoundByGroup.put(new GroupKey(row.competition(), row.groupNumber(), row.phase()),
                    row.currentRound());
        }

        Map<JornadaKey, List<MatchCalendarEntry>> entriesByJornada = new LinkedHashMap<>();
        for (MatchCalendarEntry entry : entries) {
            GroupKey group = new GroupKey(entry.competition(), entry.groupNumber(), entry.phase());
            if (!currentRoundByGroup.containsKey(group)) {
                throw new IllegalStateException("Match %s belongs to a group with no round progress: %s"
                        .formatted(entry.matchId(), group));
            }
            entriesByJornada.computeIfAbsent(new JornadaKey(group, entry.round()), key -> new ArrayList<>())
                    .add(entry);
        }

        List<JornadaProgress> jornadas = new ArrayList<>(entriesByJornada.size());
        for (Map.Entry<JornadaKey, List<MatchCalendarEntry>> jornada : entriesByJornada.entrySet()) {
            GroupKey group = jornada.getKey().group();
            jornadas.add(build(group, jornada.getKey().round(), currentRoundByGroup.get(group),
                    jornada.getValue(), overdueMarkedMatchIds, today, grace));
        }

        jornadas.sort(Comparator
                .comparing(JornadaProgress::competition, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JornadaProgress::groupNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JornadaProgress::phase, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(JornadaProgress::round));
        return List.copyOf(jornadas);
    }

    private static JornadaProgress build(GroupKey group, int round, Integer currentRound,
                                         List<MatchCalendarEntry> entries, Set<UUID> overdueMarkedMatchIds,
                                         LocalDate today, OverdueGracePeriod grace) {
        long scheduled = 0;
        long played = 0;
        long postponed = 0;
        long overdue = 0;
        long awaiting = 0;
        long undated = 0;
        LocalDate firstDate = null;
        LocalDate lastDate = null;
        for (MatchCalendarEntry entry : entries) {
            boolean marked = entry.status() == MatchStatus.SCHEDULED
                    && overdueMarkedMatchIds.contains(entry.matchId());
            CalendarMatchState state = CalendarStateResolver.resolve(entry.status(), entry.round(),
                    entry.matchDate(), currentRound, marked, today, grace);
            if (state == CalendarMatchState.PLAYED) {
                played++;
            } else {
                scheduled++;
                switch (state) {
                    case POSTPONED -> postponed++;
                    case OVERDUE -> overdue++;
                    case AWAITING_RESULT -> awaiting++;
                    case UNDATED -> undated++;
                    default -> { }
                }
            }
            LocalDate date = entry.matchDate();
            if (date != null) {
                if (firstDate == null || date.isBefore(firstDate)) {
                    firstDate = date;
                }
                if (lastDate == null || date.isAfter(lastDate)) {
                    lastDate = date;
                }
            }
        }
        return new JornadaProgress(group.competition(), group.groupNumber(), group.phase(), round,
                firstDate, lastDate, scheduled, played, postponed, overdue, awaiting, undated,
                scheduled == 0, Objects.equals(currentRound, round));
    }

    private record GroupKey(String competition, Integer groupNumber, String phase) {
    }

    private record JornadaKey(GroupKey group, int round) {
    }
}
