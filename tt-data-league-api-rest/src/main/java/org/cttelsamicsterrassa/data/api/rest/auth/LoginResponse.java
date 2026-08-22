package org.cttelsamicsterrassa.data.api.rest.auth;

public record LoginResponse(String token, String type, String username) {
    public static LoginResponse createForBearer(String token, String username) {
        return new LoginResponse(token, "Bearer", username);
    }
}
