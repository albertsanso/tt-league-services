package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.PasswordRecoveryToken;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.PasswordRecoveryTokenRepository;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Named
public class PasswordRecoveryService {
    private static final Duration DEFAULT_TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordRecoveryTokenRepository tokenRepository;
    private final UserService userService;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Duration tokenLifetime;

    @Inject
    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordRecoveryTokenRepository tokenRepository,
            UserService userService,
            UserValidator userValidator,
            PasswordEncoder passwordEncoder) {
        this(userRepository, tokenRepository, userService, userValidator, passwordEncoder,
                new SecureRandom(), DEFAULT_TOKEN_LIFETIME);
    }

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordRecoveryTokenRepository tokenRepository,
            UserService userService,
            UserValidator userValidator,
            PasswordEncoder passwordEncoder,
            SecureRandom secureRandom,
            Duration tokenLifetime) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userService = userService;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
        this.tokenLifetime = tokenLifetime;
    }

    /**
     * Creates a notification payload for the delivery adapter. The REST layer must not return it.
     */
    public Optional<RecoveryNotification> requestRecovery(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();
        ZonedDateTime now = ZonedDateTime.now();
        tokenRepository.consumeAllForUser(user.getId(), now);

        String rawToken = generateToken();
        PasswordRecoveryToken token = PasswordRecoveryToken.createNew(
                user.getId(),
                hashToken(rawToken),
                now,
                now.plus(tokenLifetime));
        tokenRepository.save(token);
        return Optional.of(new RecoveryNotification(user.getEmail(), rawToken));
    }

    public void resetPassword(String rawToken, String newPlainPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRecoveryTokenException();
        }
        if (!userValidator.validatePassword(newPlainPassword).isEmpty()) {
            throw new ValidationException("Invalid password format");
        }

        ZonedDateTime now = ZonedDateTime.now();
        PasswordRecoveryToken token = tokenRepository
                .findActiveByTokenHash(hashToken(rawToken), now)
                .orElseThrow(InvalidRecoveryTokenException::new);
        if (!tokenRepository.consumeIfActive(token.id(), now)) {
            throw new InvalidRecoveryTokenException();
        }
        userService.changeUserPassword(token.userId(), newPlainPassword);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record RecoveryNotification(String email, String token) {
    }
}
