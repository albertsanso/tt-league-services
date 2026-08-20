package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Where the match was played ({@code lugar}). The whole object, and either field, may be absent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaVenue(
        @JsonProperty("ciudad") String city,
        @JsonProperty("recinto") String venue) {
}
