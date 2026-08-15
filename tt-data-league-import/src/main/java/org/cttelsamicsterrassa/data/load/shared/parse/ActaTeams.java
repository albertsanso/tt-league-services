package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The two sides of the match ({@code equipos}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaTeams(
        @JsonProperty("local") ActaTeam home,
        @JsonProperty("visitante") ActaTeam away) {
}
