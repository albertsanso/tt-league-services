package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The doubles pairs of the match ({@code dobles}), when the competition format includes a doubles
 * game. Each member includes their name and licence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaDoubles(
        @JsonProperty("local") List<ActaLineupPlayer> home,
        @JsonProperty("visitante") List<ActaLineupPlayer> away) {

    public ActaDoubles {
        home = home == null ? List.of() : List.copyOf(home);
        away = away == null ? List.of() : List.copyOf(away);
    }
}
