package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One game within the match ({@code partidos[]}), singles or doubles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaGame(
        @JsonProperty("numero") Integer number,
        @JsonProperty("tipo") String type,
        @JsonProperty("cruce") String crossover,
        @JsonProperty("local") ActaParticipant home,
        @JsonProperty("visitante") ActaParticipant away,
        @JsonProperty("sets") List<ActaSet> sets,
        @JsonProperty("resultado_juegos") ActaScore setsWon,
        @JsonProperty("ganador") String winner,
        @JsonProperty("marcador_acumulado") ActaScore cumulativeScore,
        @JsonProperty("no_disputado") Boolean notPlayed,
        @JsonProperty("motivo") String reason) {

    /** Value of {@code tipo} for a doubles game. */
    public static final String TYPE_DOUBLES = "dobles";

    /** Value of {@code ganador} naming the home side. */
    public static final String WINNER_HOME = "local";

    /** Value of {@code ganador} naming the away side. */
    public static final String WINNER_AWAY = "visitante";

    public ActaGame {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    public boolean isDoubles() {
        return TYPE_DOUBLES.equalsIgnoreCase(type);
    }

    public boolean wasNotPlayed() {
        return Boolean.TRUE.equals(notPlayed);
    }
}
