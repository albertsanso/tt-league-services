package org.cttelsamicsterrassa.data.load.shared.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A named person appearing on a match report: delegate, coach or referee.
 * The licence is optional and is kept as text to preserve leading zeros.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaPerson(
        @JsonProperty("nombre") String name,
        @JsonProperty("licencia") String license) {
}
