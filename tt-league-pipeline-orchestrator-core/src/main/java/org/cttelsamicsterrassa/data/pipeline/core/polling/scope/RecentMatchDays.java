package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;

/**
 * The lookback of adaptive polling: within each group (a match-day key without its round: competition, group number
 * and phase) only the {@code perGroup} pollable days with the highest rounds are kept. Pollable days are past or
 * current by construction, so these are the latest past match days of the group; older ones are left to the full
 * refresh. Pure: no clock and no I/O. Only {@code AdaptivePollingTick} applies it; the manual {@code OPEN_MATCH_DAYS}
 * resolver and the match-day refresh never limit.
 */
public final class RecentMatchDays {

    private RecentMatchDays() {
    }

    /** Keeps the {@code perGroup} highest-round days of each group, in the order of {@code open}. */
    public static List<OpenMatchDay> limit(List<OpenMatchDay> open, int perGroup) {
        Objects.requireNonNull(open, "open is required");
        if (perGroup < 1) {
            throw new IllegalArgumentException("perGroup must be at least 1");
        }
        Map<Group, List<OpenMatchDay>> byGroup = new LinkedHashMap<>();
        for (OpenMatchDay day : open) {
            byGroup.computeIfAbsent(Group.of(day.key()), key -> new ArrayList<>()).add(day);
        }
        Set<OpenMatchDay> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<OpenMatchDay> days : byGroup.values()) {
            days.stream()
                    .sorted(Comparator.comparingInt((OpenMatchDay day) -> day.key().round()).reversed())
                    .limit(perGroup)
                    .forEach(kept::add);
        }
        return open.stream().filter(kept::contains).toList();
    }

    private record Group(String competition, Integer groupNumber, String phase) {

        static Group of(MatchDayKey key) {
            return new Group(key.competition(), key.groupNumber(), key.phase());
        }
    }
}
