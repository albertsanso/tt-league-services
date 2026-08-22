package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");
    private static final ZonedDateTime CREATED_AT = ZonedDateTime.of(
            2026, 8, 22, 8, 0, 0, 0, ZoneId.of("Europe/Madrid"));

    @Test
    void returnsTheUserRepresentedByTheAuthenticatedPrincipal() {
        UserService userService = mock(UserService.class);
        UserController controller = controllerWith(userService);
        User user = User.createExisting(USER_ID, CREATED_AT, "alice", "alice@example.com", "$2a$12$hash", true);
        when(userService.getUserByUsername("alice")).thenReturn(Optional.of(user));

        var response = controller.getCurrentUser(
                UsernamePasswordAuthenticationToken.authenticated("alice", null, java.util.List.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new UserDto(USER_ID, "alice", "alice@example.com", CREATED_AT, true), response.getBody());
        assertFalse(response.getBody().toString().contains("password"));
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedPrincipal() {
        UserController controller = controllerWith(mock(UserService.class));

        var response = controller.getCurrentUser(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private static UserController controllerWith(UserService userService) {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        return controller;
    }
}
