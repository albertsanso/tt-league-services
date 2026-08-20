package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A player listed under a lineup letter ({@code alineaciones.<side>.<letter>}).
 *
 * <p>The name is kept exactly as written in the source file. Some reports carry case or accent
 * damage introduced upstream (for example {@code JIMéNEZ}); it is not normalised here, so the
 * stored value always matches the federation record.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaLineupPlayer(
        @JsonProperty("id") String rfetmId,
        @JsonProperty("nombre") String name,
        @JsonProperty("licencia") String license,
        @JsonProperty("ranking") Double ranking) {
}
