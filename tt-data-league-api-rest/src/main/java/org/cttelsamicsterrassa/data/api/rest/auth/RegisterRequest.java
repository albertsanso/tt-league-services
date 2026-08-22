package org.cttelsamicsterrassa.data.api.rest.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password,
        @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") String email) {
}
