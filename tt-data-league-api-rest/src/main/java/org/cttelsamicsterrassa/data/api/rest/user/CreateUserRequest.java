package org.cttelsamicsterrassa.data.api.rest.user;

import java.util.List;

public record CreateUserRequest(String username, String email, String password, List<String> roles) {
}
