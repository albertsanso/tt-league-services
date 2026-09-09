package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Seeds a fixed set of default ADMIN accounts on first startup, so a freshly deployed
 * environment is never left without any way to log in and manage users. Idempotent per
 * account: each seed user is only created if neither its username nor its email is already
 * taken, so re-running (or running when one of the two already exists) never creates
 * duplicates. The seed credentials are fixed on purpose (see {@link #SEED_USERS}) and are
 * not validated against {@link UserValidator}'s password-strength rules, which they do not
 * meet; operators must change or remove these accounts outside development.
 */
@Named
public class InitialUserProvisioningService {

    private static final Logger LOGGER = Logger.getLogger(InitialUserProvisioningService.class.getName());

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("albert", "albert@localhost", "albert"),
            new SeedUser("oscar", "oscar@localhost", "Oscar&1234"));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Inject
    public InitialUserProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void ensureDefaultUsersExist() {
        for (SeedUser seedUser : SEED_USERS) {
            if (userRepository.existsByUsername(seedUser.username()) || userRepository.existsByEmail(seedUser.email())) {
                LOGGER.info(() -> "Skipping initial user seeding for '" + seedUser.username()
                        + "': username or email already in use");
                continue;
            }
            User user = User.createNew(seedUser.username(), seedUser.email(),
                    passwordEncoder.encode(seedUser.plainPassword()));
            user.setRoles(Set.of(UserRole.ADMIN));
            userRepository.save(user);
        }
    }

    private record SeedUser(String username, String email, String plainPassword) {
    }
}
