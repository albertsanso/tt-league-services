package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A parsed RFETM match report ({@code acta_*.json}).
 *
 * <p>The record mirrors the source schema field by field and applies no interpretation: values that
 * are absent in the file stay {@code null} here. Two fields deserve care when mapping:</p>
 *
 * <ul>
 *   <li>{@link #competition()} is generic (for example {@code "Temporada 2023-2024"}) and is
 *       <em>not</em> a competition identity; the real identity comes from the directory path.</li>
 *   <li>{@link #season()} is {@code null} in a few hundred reports; the directory path is the
 *       reliable source there too.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Acta(
        @JsonProperty("federacion") String federation,
        @JsonProperty("temporada") String season,
        @JsonProperty("competicion") String competition,
        @JsonProperty("grupo") Integer group,
        @JsonProperty("jornada") Integer round,
        @JsonProperty("fecha") LocalDate date,
        @JsonProperty("hora") LocalTime time,
        @JsonProperty("lugar") ActaVenue venue,
        @JsonProperty("equipos") ActaTeams teams,
        @JsonProperty("abc_es_local") Boolean abcIsHome,
        @JsonProperty("arbitros") ActaOfficials officials,
        @JsonProperty("alineaciones") ActaLineups lineups,
        @JsonProperty("dobles") ActaDoubles doubles,
        @JsonProperty("partidos") List<ActaGame> games,
        @JsonProperty("resultado_final") ActaFinalResult finalResult,
        @JsonProperty("acta_protestada") Boolean protested) {

    public Acta {
        games = games == null ? List.of() : List.copyOf(games);
    }

    public boolean wasProtested() {
        return Boolean.TRUE.equals(protested);
    }
}
