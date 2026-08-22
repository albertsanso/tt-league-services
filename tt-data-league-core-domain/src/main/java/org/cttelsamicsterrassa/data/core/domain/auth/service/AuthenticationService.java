package org.cttelsamicsterrassa.data.core.domain.auth.service;

import org.cttelsamicsterrassa.data.core.domain.auth.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Named
public class AuthenticationService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private final UserRepository userRepository;
    private final UserValidator userValidator;

    @Inject
    public AuthenticationService(UserRepository userRepository, UserValidator userValidator) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
    }

    public User registerUser(String username, String email, String plainPassword) {
        userValidator.validateOrThrow(username, email, plainPassword);

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        User user = User.createExisting(
                UUID.randomUUID(),
                ZonedDateTime.now(),
                username,
                email,
                PASSWORD_ENCODER.encode(plainPassword),
                true
        );

        userRepository.save(user);
        return user;
    }

    public Optional<User> authenticateUser(String username, String plainPassword) {
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();
        if (!user.isActived()) {
            return Optional.empty();
        }

        boolean isValidPassword = PASSWORD_ENCODER.matches(plainPassword, user.getPasswordHash());
        if (!isValidPassword) {
            return Optional.empty();
        }

        return Optional.of(user);
    }


    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.disable();
        userRepository.save(user);
    }

    public void enableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.enable();
        userRepository.save(user);
    }

    public void changeUserPassword(UUID userId, String newPlainPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!userValidator.validatePassword(newPlainPassword).isEmpty()) {
            throw new ValidationException("Invalid password format");
        }

        userRepository.save(User.createExisting(
                user.getId(),
                user.getCreatedAt(),
                user.getUsername(),
                user.getEmail(),
                PASSWORD_ENCODER.encode(newPlainPassword),
                user.isActived()
        ));
    }
}
