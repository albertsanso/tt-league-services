package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

/**
 * One source fixture id ({@code id_partido}) seen on more than one file of the same snapshot
 * (FEAT-00088, risk K14): the duplicated id and the file locations carrying it. A duplicate is a
 * warning, not an error; the fixture counts once in the projected progress.
 */
public record PreviewDuplicateFixtureId(
        String sourceFixtureId,
        List<String> locations) {

    public PreviewDuplicateFixtureId {
        if (sourceFixtureId == null || sourceFixtureId.isBlank()) {
            throw new IllegalArgumentException("sourceFixtureId is required");
        }
        locations = locations == null ? List.of() : List.copyOf(locations);
        if (locations.size() < 2) {
            throw new IllegalArgumentException(
                    "a duplicate fixture id needs at least 2 locations, was " + locations.size());
        }
    }
}
