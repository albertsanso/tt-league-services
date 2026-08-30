package org.cttelsamicsterrassa.data.api.rest.user;

import io.swagger.v3.oas.annotations.Operation;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.ActiveUserDeletionException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.LastAdminException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.SelfDeactivationException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAdminService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAlreadyExistsException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserNotFoundException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@UserOpenAPIv1Controller
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAdminService userAdminService;

    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "Returns the user represented by the authenticated, non-expired JWT")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userService.getUserByUsername(authentication.getName()).orElse(null);
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok(UserDto.fromObject(user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "List users (paginated)", description = "Returns a paginated, optionally filtered list of users. Requires USERS_READ.")
    public ResponseEntity<UserPageDto> listUsers(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        UserFilter filter = UserFilter.of(search, active, page, size);
        return ResponseEntity.ok(UserPageDto.fromDomain(userAdminService.listUsers(filter)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Get user by ID", description = "Returns a single user. Requires USERS_READ.")
    public ResponseEntity<?> getUserById(@PathVariable("id") UUID id) {
        try {
            return ResponseEntity.ok(UserDto.fromObject(userAdminService.getUser(id)));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Create a user", description = "Creates a new user with the specified profile and roles. Requires USERS_WRITE.")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (request == null || request.username() == null || request.email() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "username, email, and password are required"));
        }
        try {
            Set<UserRole> roles = parseRoles(request.roles());
            User created = userAdminService.createUser(request.username(), request.email(), request.password(), roles);
            return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.fromObject(created));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Update a user's profile and roles", description = "Updates username, email, and/or roles. Requires USERS_WRITE.")
    public ResponseEntity<?> updateUser(@PathVariable("id") UUID id, @RequestBody UpdateUserRequest request) {
        if (request == null || request.username() == null || request.email() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "username and email are required"));
        }
        try {
            Set<UserRole> roles = parseRoles(request.roles());
            User updated = userAdminService.updateUser(id, request.username(), request.email(), roles);
            return ResponseEntity.ok(UserDto.fromObject(updated));
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Activate or deactivate a user", description = "Sets the active state. Cannot deactivate self or the last admin. Requires USERS_WRITE.")
    public ResponseEntity<?> setUserActive(
            @PathVariable("id") UUID id,
            @RequestBody PatchUserActiveRequest request,
            Authentication authentication) {
        if (request == null || request.active() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "active field is required"));
        }
        UUID currentUserId = resolveCurrentUserId(authentication);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            userAdminService.setUserActive(id, request.active(), currentUserId);
            return ResponseEntity.noContent().build();
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (SelfDeactivationException | LastAdminException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Permanently delete a deactivated user", description = "Permanently deletes a user that is already deactivated. Active users cannot be deleted. Requires USERS_WRITE.")
    public ResponseEntity<?> deleteUser(@PathVariable("id") UUID id) {
        try {
            userAdminService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (ActiveUserDeletionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (LastAdminException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Get role catalog", description = "Returns all available roles and their derived permissions.")
    public ResponseEntity<List<RoleDto>> getRoles() {
        return ResponseEntity.ok(RoleDto.catalog());
    }

    private Set<UserRole> parseRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return EnumSet.of(UserRole.PRACTITIONER);
        }
        Set<UserRole> result = EnumSet.noneOf(UserRole.class);
        for (String name : roleNames) {
            try {
                result.add(UserRole.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown role: " + name);
            }
        }
        return result;
    }

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication == null) return null;
        User current = userService.getUserByUsername(authentication.getName()).orElse(null);
        return current == null ? null : current.getId();
    }
}
