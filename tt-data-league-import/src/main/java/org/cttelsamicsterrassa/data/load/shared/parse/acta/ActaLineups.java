package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lineups per side ({@code alineaciones}), each a map of lineup letter to player.
 *
 * <p>A side uses either A/B/C or X/Y/Z, as indicated by {@link Acta#abcIsHome()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaLineups(
        @JsonProperty("local") Map<String, ActaLineupPlayer> home,
        @JsonProperty("visitante") Map<String, ActaLineupPlayer> away) {

    public ActaLineups {
        home = copyOf(home);
        away = copyOf(away);
    }

    private static Map<String, ActaLineupPlayer> copyOf(Map<String, ActaLineupPlayer> letters) {
        return letters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(letters));
    }
}
