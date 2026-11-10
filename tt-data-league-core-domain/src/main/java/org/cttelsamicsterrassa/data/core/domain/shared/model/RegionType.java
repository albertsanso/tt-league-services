package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The level of the league a match belongs to. A closed vocabulary, see {@link Region}.
 *
 * <ul>
 *   <li>{@link #NACIONAL}: Spain-wide competition (RFETM)</li>
 *   <li>{@link #AUTONOMICA}: Catalonia-wide competition (FCTT TDM, Copa Catalana Femenina)</li>
 *   <li>{@link #PROVINCIAL}: provincial representation league (BCNESA)</li>
 *   <li>{@link #COMARCAL}: BCNESA league whose category is a comarcal one</li>
 * </ul>
 */
public enum RegionType {
    NACIONAL,
    AUTONOMICA,
    PROVINCIAL,
    COMARCAL;

    /** The wire value used by the acta JSON, the REST API and the database: the lower-case name. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The type named {@code value} (case-insensitive); empty for {@code null}, blank or unknown input. */
    public static Optional<RegionType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim();
        for (RegionType type : values()) {
            if (type.name().equalsIgnoreCase(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
