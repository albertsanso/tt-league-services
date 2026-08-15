package org.cttelsamicsterrassa.data.core.domain.shared.model;

import java.time.Year;
import java.util.Objects;

public record Season(Year start, Year end) {

    public Season {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!end.equals(start.plusYears(1))) {
            throw new IllegalArgumentException(
                    "A season must span exactly one year: %s-%s".formatted(start, end));
        }
    }

    public static Season of(int startYear) {
        Year s = Year.of(startYear);
        return new Season(s, s.plusYears(1));
    }

    public static Season of(Year start, Year end) {
        return new Season(start, end);
    }

    public static Season of(int startYear, int endYear) {
        return new Season(Year.of(startYear), Year.of(endYear));
    }

    public static Season fromFormatted(String formatted) {
        String[] parts = formatted.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid season format: " + formatted);
        }
        Year start = Year.parse(parts[0]);
        Year end = Year.parse(parts[1]);
        return new Season(start, end);
    }

    @Override
    public String toString() {
        return "%s-%s".formatted(start, end);
    }
}
