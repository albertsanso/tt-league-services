package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAdminServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_ID  = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

    private static UserAdminService serviceWith(UserRepository repo) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        return new UserAdminService(repo, new UserValidator(), encoder);
    }

    private static User adminUser() {
        return User.createExisting(ADMIN_ID, NOW, "admin", "admin@example.com", "$2a$12$hash", true,
                Set.of(UserRole.ADMIN));
    }

    private static User regularUser() {
        return User.createExisting(USER_ID, NOW, "bob", "bob@example.com", "$2a$12$hash", true,
                Set.of(UserRole.PRACTITIONER));
    }

    // --- listUsers ---

    @Test
    void listUsersReturnsPageFromRepository() {
        UserRepository repo = mock(UserRepository.class);
        UserPage page = UserPage.of(List.of(adminUser()), 1, 0, 20);
        when(repo.findPage(any())).thenReturn(page);

        UserPage result = serviceWith(repo).listUsers(UserFilter.defaults());

        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
    }

    // --- getUser ---

    @Test
    void getUserThrowsNotFoundWhenMissing() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> serviceWith(repo).getUser(USER_ID));
    }

    @Test
    void getUserReturnsUserWhenPresent() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(regularUser()));

        User result = serviceWith(repo).getUser(USER_ID);
        assertEquals("bob", result.getUsername());
    }

    // --- createUser ---

    @Test
    void createUserThrowsOnDuplicateUsername() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByUsername("bob")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> serviceWith(repo).createUser("bob", "bob2@example.com", "P@ssw0rd!", Set.of()));
    }

    @Test
    void createUserThrowsOnDuplicateEmail() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByEmail("bob@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> serviceWith(repo).createUser("bob2", "bob@example.com", "P@ssw0rd!", Set.of()));
    }

    @Test
    void createUserPersistsUser() {
        UserRepository repo = mock(UserRepository.class);

        User created = serviceWith(repo).createUser("carol", "carol@example.com", "P@ssw0rd!", Set.of(UserRole.ANALYST));

        verify(repo).save(any(User.class));
        assertEquals("carol", created.getUsername());
        assertTrue(created.getRoles().contains(UserRole.ANALYST));
    }

    @Test
    void createUserThrowsValidationOnBadPassword() {
        UserRepository repo = mock(UserRepository.class);
        assertThrows(ValidationException.class,
                () -> serviceWith(repo).createUser("carol", "carol@example.com", "short", Set.of()));
    }

    // --- updateUser ---

    @Test
    void updateUserChangesUsernameAndEmail() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(regularUser()));

        User updated = serviceWith(repo).updateUser(USER_ID, "bobby", "bobby@example.com", Set.of(UserRole.PRACTITIONER));

        assertEquals("bobby", updated.getUsername());
        assertEquals("bobby@example.com", updated.getEmail());
        verify(repo).save(any(User.class));
    }

    @Test
    void updateUserThrowsConflictOnDuplicateUsername() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(regularUser()));
        when(repo.existsByUsername("taken")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class,
                () -> serviceWith(repo).updateUser(USER_ID, "taken", "bob@example.com", Set.of()));
    }

    // --- setUserActive ---

    @Test
    void deactivatingSelfThrowsSelfDeactivationException() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser()));

        assertThrows(SelfDeactivationException.class,
                () -> serviceWith(repo).setUserActive(ADMIN_ID, false, ADMIN_ID));
    }

    @Test
    void deactivatingLastAdminThrowsLastAdminException() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(
                User.createExisting(USER_ID, NOW, "alice", "alice@example.com", "$hash", true,
                        Set.of(UserRole.ADMIN))));
        when(repo.countActiveAdmins()).thenReturn(1L);

        assertThrows(LastAdminException.class,
                () -> serviceWith(repo).setUserActive(USER_ID, false, ADMIN_ID));
    }

    @Test
    void deactivatingRegularUserSucceeds() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(regularUser()));

        serviceWith(repo).setUserActive(USER_ID, false, ADMIN_ID);

        verify(repo).save(any(User.class));
    }

    @Test
    void activatingUserSucceeds() {
        UserRepository repo = mock(UserRepository.class);
        User inactive = User.createExisting(USER_ID, NOW, "bob", "bob@example.com", "$hash", false);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(inactive));

        serviceWith(repo).setUserActive(USER_ID, true, ADMIN_ID);

        verify(repo).save(any(User.class));
    }

    @Test
    void deactivatingOneOfManyAdminsSucceeds() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(
                User.createExisting(USER_ID, NOW, "alice", "alice@example.com", "$hash", true,
                        Set.of(UserRole.ADMIN))));
        when(repo.countActiveAdmins()).thenReturn(2L);

        serviceWith(repo).setUserActive(USER_ID, false, ADMIN_ID);

        verify(repo).save(any(User.class));
    }
}
