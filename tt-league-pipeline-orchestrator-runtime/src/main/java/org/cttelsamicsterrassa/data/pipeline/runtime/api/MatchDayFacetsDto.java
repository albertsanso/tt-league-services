package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayFacets;

/** The values the match-day list can be filtered by; {@code competitions} is what the UI calls the category. */
public record MatchDayFacetsDto(List<String> seasons, List<String> competitions, List<String> phases) {

    static MatchDayFacetsDto from(MatchDayFacets facets) {
        return new MatchDayFacetsDto(facets.seasons(), facets.competitions(), facets.phases());
    }
}
