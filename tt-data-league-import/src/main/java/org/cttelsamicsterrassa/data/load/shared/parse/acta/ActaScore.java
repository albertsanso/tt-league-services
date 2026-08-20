package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A home/away score pair ({@code marcador}). Either side may be {@code null} in incomplete reports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaScore(
        @JsonProperty("local") Integer home,
        @JsonProperty("visitante") Integer away) {
}
