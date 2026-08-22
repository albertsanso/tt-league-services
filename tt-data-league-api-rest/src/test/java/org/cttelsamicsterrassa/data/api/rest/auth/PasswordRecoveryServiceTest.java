package org.cttelsamicsterrassa.data.api.rest.auth;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.PasswordRecoveryTokenRepository;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.InvalidRecoveryTokenException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.PasswordRecoveryService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceTest {
    @Test
    void recoveryTokensAreStoredHashedAndCanOnlyBeConsumedOnce() {
        UserRepository users = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        InMemoryRecoveryRepository tokens = new InMemoryRecoveryRepository();
        User user = User.createExisting(
                UUID.randomUUID(),
                ZonedDateTime.now(),
                "alice",
                "alice@example.com",
                "old-hash",
                true);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(encoder.encode("new-password")).thenReturn("new-hash");

        PasswordRecoveryService service = new PasswordRecoveryService(
                users,
                tokens,
                userService,
                new UserValidator(),
                encoder,
                new java.security.SecureRandom(),
                Duration.ofMinutes(30));

        String rawToken = service.requestRecovery("alice@example.com").orElseThrow().token();
        assertTrue(tokens.saved.tokenHash().matches("[0-9a-f]{64}"));
        service.resetPassword(rawToken, "New-password1!");
        verify(userService).changeUserPassword(user.getId(), "New-password1!");
        assertThrows(InvalidRecoveryTokenException.class,
                () -> service.resetPassword(rawToken, "New-password1!"));
    }

    private static final class InMemoryRecoveryRepository implements PasswordRecoveryTokenRepository {
        private PasswordRecoveryToken saved;
        private final List<PasswordRecoveryToken> tokens = new ArrayList<>();

        @Override
        public void save(PasswordRecoveryToken token) {
            saved = token;
            tokens.add(token);
        }

        @Override
        public Optional<PasswordRecoveryToken> findActiveByTokenHash(String tokenHash, ZonedDateTime now) {
            return tokens.stream()
                    .filter(token -> token.tokenHash().equals(tokenHash) && token.isActiveAt(now))
                    .findFirst();
        }

        @Override
        public boolean consumeIfActive(UUID tokenId, ZonedDateTime now) {
            for (int index = 0; index < tokens.size(); index++) {
                PasswordRecoveryToken token = tokens.get(index);
                if (token.id().equals(tokenId) && token.isActiveAt(now)) {
                    PasswordRecoveryToken consumed = new PasswordRecoveryToken(
                            token.id(), token.userId(), token.tokenHash(), token.createdAt(),
                            token.expiresAt(), true);
                    tokens.set(index, consumed);
                    saved = consumed;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void consumeAllForUser(UUID userId, ZonedDateTime now) {
            tokens.removeIf(token -> token.userId().equals(userId) && token.isActiveAt(now));
        }
    }
}
