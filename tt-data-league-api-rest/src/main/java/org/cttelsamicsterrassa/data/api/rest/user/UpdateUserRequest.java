package org.cttelsamicsterrassa.data.api.rest.user;

import java.util.List;

public record UpdateUserRequest(String username, String email, List<String> roles) {
}
