package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The overall outcome of the match ({@code resultado_final}).
 *
 * <p>{@link #winnerName()} is a club <em>name</em>, not an id, so it is only usable by comparing it
 * against the two team names of this same report.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaFinalResult(
        @JsonProperty("ganador") String winnerName,
        @JsonProperty("marcador_partidos") ActaScore gamesWon,
        @JsonProperty("marcador_juegos") ActaScore setsWon) {
}
