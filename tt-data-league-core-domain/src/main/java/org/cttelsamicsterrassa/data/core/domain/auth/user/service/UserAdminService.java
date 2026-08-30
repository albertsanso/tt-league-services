package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administrator-facing user management operations. Enforces self-protection and
 * last-admin constraints. Registration and authentication remain in their own services.
 */
@Named
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;

    @Inject
    public UserAdminService(UserRepository userRepository, UserValidator userValidator,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userValidator = userValidator;
        this.passwordEncoder = passwordEncoder;
    }

    public UserPage listUsers(UserFilter filter) {
        return userRepository.findPage(filter);
    }

    public User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public User createUser(String username, String email, String plainPassword, Set<UserRole> roles) {
        List<String> errors = new java.util.ArrayList<>();
        errors.addAll(userValidator.validateUsername(username));
        errors.addAll(userValidator.validateEmail(email));
        errors.addAll(userValidator.validatePassword(plainPassword));
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }

        User user = User.createNew(username, email, passwordEncoder.encode(plainPassword));
        if (roles != null && !roles.isEmpty()) {
            user.setRoles(roles);
        }
        userRepository.save(user);
        return user;
    }

    public User updateUser(UUID id, String username, String email, Set<UserRole> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (!user.getUsername().equals(username)) {
            List<String> errors = userValidator.validateUsername(username);
            if (!errors.isEmpty()) {
                throw new ValidationException(String.join("; ", errors));
            }
            if (userRepository.existsByUsername(username)) {
                throw new UserAlreadyExistsException("Username already exists: " + username);
            }
            user.changeUsername(username);
        }

        if (!user.getEmail().equals(email)) {
            List<String> errors = userValidator.validateEmail(email);
            if (!errors.isEmpty()) {
                throw new ValidationException(String.join("; ", errors));
            }
            if (userRepository.existsByEmail(email)) {
                throw new UserAlreadyExistsException("Email already exists: " + email);
            }
            user.changeEmail(email);
        }

        if (roles != null) {
            user.setRoles(roles);
        }

        userRepository.save(user);
        return user;
    }

    /**
     * Activates or deactivates a user. Prevents self-deactivation and removal
     * of the last active administrator.
     *
     * @param targetId      user to activate or deactivate
     * @param active        desired state
     * @param currentUserId the administrator performing the operation
     */
    public void setUserActive(UUID targetId, boolean active, UUID currentUserId) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        if (!active) {
            if (targetId.equals(currentUserId)) {
                throw new SelfDeactivationException();
            }
            boolean targetIsAdmin = target.getRoles().contains(UserRole.ADMIN);
            if (targetIsAdmin && userRepository.countActiveAdmins() <= 1) {
                throw new LastAdminException();
            }
            target.disable();
        } else {
            target.enable();
        }
        userRepository.save(target);
    }
}
