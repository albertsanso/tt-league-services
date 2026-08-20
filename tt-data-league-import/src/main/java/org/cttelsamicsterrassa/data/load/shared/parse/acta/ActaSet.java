package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The point score of a single set within a game.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaSet(
        @JsonProperty("set") Integer number,
        @JsonProperty("local") Integer homePoints,
        @JsonProperty("visitante") Integer awayPoints) {
}
