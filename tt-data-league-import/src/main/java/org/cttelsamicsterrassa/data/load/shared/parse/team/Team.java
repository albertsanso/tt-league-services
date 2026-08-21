package org.cttelsamicsterrassa.data.load.shared.parse.team;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.regex.Pattern;

/**
 * One team participation from an {@code equipos-json/*.json} file.
 *
 * <p>The source schema uses snake-case property names and keeps the season and category as the
 * federation's original strings. This value mirrors those fields without mapping them to domain
 * entities or applying source-specific identity rules.</p>
 */
public record Team(
        @JsonProperty("season") String season,
        @JsonProperty("club_name") String clubName,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("category") String category) {

    private static final Pattern SEASON_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{4}$");
    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("^(?:SUF|SUM|[A-Z0-9]+(?:-[A-Z0-9]+)+(?:/[A-Z0-9]+)?)$");

    public Team {
        if (season == null || !SEASON_PATTERN.matcher(season).matches()) {
            throw new IllegalArgumentException("season must match YYYY-YYYY");
        }
        if (clubName == null || clubName.isEmpty()) {
            throw new IllegalArgumentException("club_name must not be empty");
        }
        if (teamName == null || teamName.isEmpty()) {
            throw new IllegalArgumentException("team_name must not be empty");
        }
        if (category == null || !CATEGORY_PATTERN.matcher(category).matches()) {
            throw new IllegalArgumentException("category has an invalid format");
        }
    }
}
