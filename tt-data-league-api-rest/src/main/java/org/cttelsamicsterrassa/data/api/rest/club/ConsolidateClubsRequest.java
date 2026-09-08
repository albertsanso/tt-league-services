package org.cttelsamicsterrassa.data.api.rest.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ConsolidateClubsRequest(
        @Size(min = 2, message = "At least two clubs must be selected for consolidation")
        List<UUID> clubIds,

        @NotBlank(message = "Canonical name must not be blank")
        @Size(min = 2, max = 255, message = "Canonical name must contain between 2 and 255 characters")
        String canonicalName,

        @NotNull(message = "Primary club must be provided")
        UUID primaryClubId) {
}
