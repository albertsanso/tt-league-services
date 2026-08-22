package org.cttelsamicsterrassa.data.api.rest.auth;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "Recovery token is required")
        String token,
        @NotBlank(message = "Password is required")
        String password) {
}
