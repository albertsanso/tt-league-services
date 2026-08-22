package org.cttelsamicsterrassa.data.core.repository.jpa.auth;

import org.cttelsamicsterrassa.data.core.domain.auth.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserRepositoryJpaTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");
    private static final ZonedDateTime CREATED_AT = ZonedDateTime.of(
            2026, 8, 22, 8, 0, 0, 0, ZoneId.of("Europe/Madrid"));

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsAndFindsUsersByIdUsernameAndEmail() {
        User user = user("alice", "alice@example.com", true);

        userRepository.save(user);

        assertEquals(user.getId(), userRepository.findById(ID).orElseThrow().getId());
        assertEquals(ID, userRepository.findByUsername("alice").orElseThrow().getId());
        assertEquals(ID, userRepository.findByEmail("alice@example.com").orElseThrow().getId());
        assertTrue(userRepository.existsByUsername("alice"));
        assertTrue(userRepository.existsByEmail("alice@example.com"));
        assertFalse(userRepository.existsByUsername("missing"));
        assertFalse(userRepository.existsByEmail("missing@example.com"));
    }

    @Test
    void findsAllUsersAndUpdatesAnExistingUser() {
        User user = user("alice", "alice@example.com", true);
        userRepository.save(user);

        user.disable();
        userRepository.save(user);

        assertEquals(1, userRepository.findAll().size());
        assertFalse(userRepository.findById(ID).orElseThrow().isActived());
    }

    private static User user(String username, String email, boolean active) {
        return User.createExisting(ID, CREATED_AT, username, email, "$2a$12$hash", active);
    }
}
