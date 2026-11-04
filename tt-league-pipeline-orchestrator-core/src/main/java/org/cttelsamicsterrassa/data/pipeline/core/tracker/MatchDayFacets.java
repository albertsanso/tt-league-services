package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.List;
import java.util.Objects;

/** The distinct, sorted values the match-day list can be filtered by. */
public record MatchDayFacets(List<String> seasons, List<String> competitions, List<String> phases) {

    public MatchDayFacets {
        seasons = List.copyOf(Objects.requireNonNull(seasons, "seasons is required"));
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions is required"));
        phases = List.copyOf(Objects.requireNonNull(phases, "phases is required"));
    }
}
