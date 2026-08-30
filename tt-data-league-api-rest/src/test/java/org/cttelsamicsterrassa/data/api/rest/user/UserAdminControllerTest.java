package org.cttelsamicsterrassa.data.api.rest.user;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.LastAdminException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.SelfDeactivationException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAdminService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAlreadyExistsException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserNotFoundException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAdminControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_ID  = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

    private static User adminUser() {
        return User.createExisting(ADMIN_ID, NOW, "admin", "admin@example.com", "$hash", true, Set.of(UserRole.ADMIN));
    }

    private static User regularUser() {
        return User.createExisting(USER_ID, NOW, "bob", "bob@example.com", "$hash", true);
    }

    private static UserController controllerWith(UserService us, UserAdminService uas) {
        UserController c = new UserController();
        ReflectionTestUtils.setField(c, "userService", us);
        ReflectionTestUtils.setField(c, "userAdminService", uas);
        return c;
    }

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of());
    }

    // --- listUsers ---

    @Test
    void listUsersReturnsPage() {
        UserAdminService uas = mock(UserAdminService.class);
        UserPage page = UserPage.of(List.of(regularUser()), 1, 0, 20);
        when(uas.listUsers(any(UserFilter.class))).thenReturn(page);

        var resp = controllerWith(mock(UserService.class), uas).listUsers(null, null, 0, 20);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().totalElements());
    }

    // --- getUserById ---

    @Test
    void getUserByIdReturns200WhenFound() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.getUser(USER_ID)).thenReturn(regularUser());

        var resp = controllerWith(mock(UserService.class), uas).getUserById(USER_ID);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void getUserByIdReturns404WhenNotFound() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.getUser(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        var resp = controllerWith(mock(UserService.class), uas).getUserById(USER_ID);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- createUser ---

    @Test
    void createUserReturns201OnSuccess() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.createUser(eq("bob"), eq("bob@example.com"), eq("P@ssw0rd!"), any()))
                .thenReturn(regularUser());

        var resp = controllerWith(mock(UserService.class), uas)
                .createUser(new CreateUserRequest("bob", "bob@example.com", "P@ssw0rd!", List.of("PRACTITIONER")));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void createUserReturns409OnConflict() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.createUser(any(), any(), any(), any())).thenThrow(new UserAlreadyExistsException("exists"));

        var resp = controllerWith(mock(UserService.class), uas)
                .createUser(new CreateUserRequest("bob", "bob@example.com", "P@ssw0rd!", null));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    void createUserReturns400OnValidationError() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.createUser(any(), any(), any(), any())).thenThrow(new ValidationException("bad password"));

        var resp = controllerWith(mock(UserService.class), uas)
                .createUser(new CreateUserRequest("bob", "bob@example.com", "weak", null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void createUserReturns400OnMissingFields() {
        var resp = controllerWith(mock(UserService.class), mock(UserAdminService.class))
                .createUser(new CreateUserRequest(null, null, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // --- updateUser ---

    @Test
    void updateUserReturns200OnSuccess() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.updateUser(eq(USER_ID), any(), any(), any())).thenReturn(regularUser());

        var resp = controllerWith(mock(UserService.class), uas)
                .updateUser(USER_ID, new UpdateUserRequest("bob", "bob@example.com", null));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void updateUserReturns404WhenNotFound() {
        UserAdminService uas = mock(UserAdminService.class);
        when(uas.updateUser(any(), any(), any(), any())).thenThrow(new UserNotFoundException(USER_ID));

        var resp = controllerWith(mock(UserService.class), uas)
                .updateUser(USER_ID, new UpdateUserRequest("bob", "bob@example.com", null));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- setUserActive ---

    @Test
    void setUserActiveReturns204OnSuccess() {
        UserService us = mock(UserService.class);
        when(us.getUserByUsername("admin")).thenReturn(Optional.of(adminUser()));
        UserAdminService uas = mock(UserAdminService.class);
        doNothing().when(uas).setUserActive(any(), anyBoolean(), any());

        var resp = controllerWith(us, uas)
                .setUserActive(USER_ID, new PatchUserActiveRequest(false), adminAuth());

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }

    @Test
    void setUserActiveReturns409OnSelfDeactivation() {
        UserService us = mock(UserService.class);
        when(us.getUserByUsername("admin")).thenReturn(Optional.of(adminUser()));
        UserAdminService uas = mock(UserAdminService.class);
        doThrow(new SelfDeactivationException()).when(uas).setUserActive(any(), anyBoolean(), any());

        var resp = controllerWith(us, uas)
                .setUserActive(ADMIN_ID, new PatchUserActiveRequest(false), adminAuth());

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    void setUserActiveReturns409OnLastAdmin() {
        UserService us = mock(UserService.class);
        when(us.getUserByUsername("admin")).thenReturn(Optional.of(adminUser()));
        UserAdminService uas = mock(UserAdminService.class);
        doThrow(new LastAdminException()).when(uas).setUserActive(any(), anyBoolean(), any());

        var resp = controllerWith(us, uas)
                .setUserActive(ADMIN_ID, new PatchUserActiveRequest(false), adminAuth());

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    }

    @Test
    void setUserActiveReturns404WhenNotFound() {
        UserService us = mock(UserService.class);
        when(us.getUserByUsername("admin")).thenReturn(Optional.of(adminUser()));
        UserAdminService uas = mock(UserAdminService.class);
        doThrow(new UserNotFoundException(USER_ID)).when(uas).setUserActive(any(), anyBoolean(), any());

        var resp = controllerWith(us, uas)
                .setUserActive(USER_ID, new PatchUserActiveRequest(false), adminAuth());

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // --- getRoles ---

    @Test
    void getRolesCatalogReturnsAllRoles() {
        var resp = controllerWith(mock(UserService.class), mock(UserAdminService.class)).getRoles();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(UserRole.values().length, resp.getBody().size());
        assertFalse(resp.getBody().stream().anyMatch(r -> r.permissions() == null));
    }
}
