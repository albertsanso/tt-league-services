package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a competition belongs. A closed vocabulary: it describes a match, it is never part of club,
 * player or fixture identity (that stays {@link ImportSource}).
 */
public enum Region {
    ESPANA,
    CATALUNYA,
    BARCELONA,
    GIRONA,
    LLEIDA,
    TARRAGONA;

    /** The wire value used by the acta JSON, the REST API and the database: the lower-case name. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The region named {@code value} (case-insensitive); empty for {@code null}, blank or unknown input. */
    public static Optional<Region> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim();
        for (Region region : values()) {
            if (region.name().equalsIgnoreCase(wanted)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }
}
