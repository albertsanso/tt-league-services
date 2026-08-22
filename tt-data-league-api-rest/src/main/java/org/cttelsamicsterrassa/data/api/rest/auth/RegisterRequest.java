package org.cttelsamicsterrassa.data.api.rest.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterRequest(String username, String password, String email) {
}

