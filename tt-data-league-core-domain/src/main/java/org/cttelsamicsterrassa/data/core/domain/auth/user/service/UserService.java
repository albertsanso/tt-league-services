package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.UUID;

@Named
public class UserService {

    private final UserValidator userValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Inject
    public UserService(UserValidator userValidator, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userValidator = userValidator;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        user.changePasswordHash(passwordEncoder.encode(newPlainPassword));
        userRepository.save(user);
    }

    public boolean verifyUserPassword(String plainPassword, User user) {
        return passwordEncoder.matches(plainPassword, user.getPasswordHash());
    }
}
