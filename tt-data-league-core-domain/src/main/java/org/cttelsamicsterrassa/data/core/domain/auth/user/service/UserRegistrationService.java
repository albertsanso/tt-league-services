package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class UserRegistrationService {

    private final UserValidator userValidator;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Inject
    public UserRegistrationService(UserValidator userValidator, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userValidator = userValidator;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String username, String email, String plainPassword) {
        userValidator.validateOrThrow(username, email, plainPassword);

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        User user = User.createNew(
                username,
                email,
                passwordEncoder.encode(plainPassword)
        );

        userRepository.save(user);
        return user;
    }
}
