package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One side of an individual game ({@code partidos[].local} / {@code partidos[].visitante}).
 *
 * <p>For singles the participant is a single player identified by lineup {@link #letter()}; for
 * doubles the letter is {@code Db} and the two members are identified by name and licence in
 * {@link #doublesPlayers()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaParticipant(
        @JsonProperty("letra") String letter,
        @JsonProperty("nombre") String name,
        @JsonProperty("licencia") String license,
        @JsonProperty("id") String rfetmId,
        @JsonProperty("ranking") Double ranking,
        @JsonProperty("jugadores") List<ActaLineupPlayer> doublesPlayers) {

    public ActaParticipant {
        doublesPlayers = doublesPlayers == null ? List.of() : List.copyOf(doublesPlayers);
    }

    public String licenseId() {
        return license;
    }
}
