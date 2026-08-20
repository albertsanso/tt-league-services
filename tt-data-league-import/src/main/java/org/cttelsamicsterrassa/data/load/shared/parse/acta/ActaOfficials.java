package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Refereeing staff recorded on the report ({@code arbitros}). The assistant is always absent in the
 * current dataset but is modelled for completeness.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActaOfficials(
        @JsonProperty("principal") ActaPerson head,
        @JsonProperty("asistente") ActaPerson assistant) {
}
