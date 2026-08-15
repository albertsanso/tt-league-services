package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One of the two sides of the match ({@code equipos.local} / {@code equipos.visitante}).
 *
 * <p>{@link #rfetmId()} is the authoritative source of team identity for the RFETM import. It is
 * absent from roughly a fifth of the reports, which cannot be attributed to a club and are skipped.
 * The report file name is never consulted for team identity: its shape varies across the export and
 * carries no reliable ids.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaTeam(
        @JsonProperty("id") String rfetmId,
        @JsonProperty("nombre") String name,
        @JsonProperty("delegado") ActaPerson delegate,
        @JsonProperty("entrenador") ActaPerson coach) {
}
