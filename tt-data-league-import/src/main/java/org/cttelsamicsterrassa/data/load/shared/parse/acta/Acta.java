package org.cttelsamicsterrassa.data.load.shared.parse.acta;

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
        @JsonProperty("id_partido") String matchId,
        @JsonProperty("acta_publicada") Boolean published,
        @JsonProperty("federacion") String federation,
        @JsonProperty("temporada") String season,
        @JsonProperty("competicion") String competition,
        @JsonProperty("grupo") Integer group,
        @JsonProperty("jornada") Integer round,
        @JsonProperty("fase") String phase,
        @JsonProperty("genero") String gender,
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
        @JsonProperty("acta_protestada") Boolean protested,
        @JsonProperty("region") String region,
        @JsonProperty("tipo_region") String regionType) {

    public Acta {
        games = games == null ? List.of() : List.copyOf(games);
    }

    /**
     * An acta without {@code region} / {@code tipo_region}: every export written before FEAT-00121 and
     * every RFETM export. The region is then derived by {@link
     * org.cttelsamicsterrassa.data.core.domain.shared.model.RegionResolver}.
     */
    public Acta(String matchId, Boolean published, String federation, String season, String competition,
                Integer group, Integer round, String phase, String gender, LocalDate date, LocalTime time,
                ActaVenue venue, ActaTeams teams, Boolean abcIsHome, ActaOfficials officials, ActaLineups lineups,
                ActaDoubles doubles, List<ActaGame> games, ActaFinalResult finalResult, Boolean protested) {
        this(matchId, published, federation, season, competition, group, round, phase, gender, date, time, venue,
                teams, abcIsHome, officials, lineups, doubles, games, finalResult, protested, null, null);
    }

    public boolean wasProtested() {
        return Boolean.TRUE.equals(protested);
    }

    /**
     * The model definition treats a missing {@code acta_publicada} as published, so RFETM and BCNESA
     * sources (which never send this field) behave as before.
     */
    public boolean isPublished() {
        return !Boolean.FALSE.equals(published);
    }
}
