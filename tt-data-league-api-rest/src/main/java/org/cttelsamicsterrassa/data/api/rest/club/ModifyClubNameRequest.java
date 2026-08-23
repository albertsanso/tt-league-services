package org.cttelsamicsterrassa.data.api.rest.club;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifyClubNameRequest(
        @NotBlank(message = "Club name must not be blank")
        @Size(min = 2, max = 255, message = "Club name must contain between 2 and 255 characters")
        String name) {
}
