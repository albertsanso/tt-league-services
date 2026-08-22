package org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper;

import org.cttelsamicsterrassa.data.core.domain.auth.model.User;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.UserJPA;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserMapperTest {

    private static final UUID ID = UUID.fromString("f8e1f5c4-1c3e-4b40-9ac2-9fc0ccf4a2db");
    private static final ZonedDateTime CREATED_AT = ZonedDateTime.of(
            2026, 8, 22, 8, 0, 0, 0, ZoneId.of("Europe/Madrid"));

    @Test
    void mapsDomainUserToJpaUser() {
        User user = User.createExisting(
                ID, CREATED_AT, "alice", "alice@example.com", "$2a$12$hash", false);

        UserJPA userJPA = new UserToUserJPAMapper().apply(user);

        assertEquals(ID, userJPA.getId());
        assertEquals("alice", userJPA.getUsername());
        assertEquals("alice@example.com", userJPA.getEmail());
        assertEquals("$2a$12$hash", userJPA.getPasswordHash());
        assertEquals(CREATED_AT, userJPA.getCreatedAt());
        assertFalse(userJPA.isActive());
    }

    @Test
    void mapsJpaUserToDomainUser() {
        UserJPA userJPA = new UserJPA();
        userJPA.setId(ID);
        userJPA.setCreatedAt(CREATED_AT);
        userJPA.setUsername("alice");
        userJPA.setEmail("alice@example.com");
        userJPA.setPasswordHash("$2a$12$hash");
        userJPA.setActive(false);

        User user = new UserJPAToUserMapper().apply(userJPA);

        assertEquals(ID, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals("$2a$12$hash", user.getPasswordHash());
        assertEquals(CREATED_AT, user.getCreatedAt());
        assertFalse(user.isActived());
    }

    @Test
    void mapsNullToNull() {
        assertNull(new UserToUserJPAMapper().apply(null));
        assertNull(new UserJPAToUserMapper().apply(null));
    }
}
