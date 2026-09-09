package org.cttelsamicsterrassa.data.core.domain.auth.user.service;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialUserProvisioningServiceTest {

    private static InitialUserProvisioningService serviceWith(UserRepository repo) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        return new InitialUserProvisioningService(repo, encoder);
    }

    @Test
    void createsBothDefaultAdminsWhenNeitherExists() {
        UserRepository repo = mock(UserRepository.class);

        serviceWith(repo).ensureDefaultUsersExist();

        verify(repo, times(2)).save(any(User.class));
        verify(repo).save(argThat(u -> u.getUsername().equals("albert")
                && u.getEmail().equals("albert@localhost")
                && u.getRoles().equals(java.util.Set.of(UserRole.ADMIN))
                && u.getPasswordHash().equals("hashed:albert")));
        verify(repo).save(argThat(u -> u.getUsername().equals("oscar")
                && u.getEmail().equals("oscar@localhost")
                && u.getRoles().equals(java.util.Set.of(UserRole.ADMIN))
                && u.getPasswordHash().equals("hashed:Oscar&1234")));
    }

    @Test
    void createsOnlyTheMissingUserWhenTheOtherAlreadyExists() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByUsername("albert")).thenReturn(true);

        serviceWith(repo).ensureDefaultUsersExist();

        verify(repo, times(1)).save(any(User.class));
        verify(repo).save(argThat(u -> u.getUsername().equals("oscar")));
    }

    @Test
    void isANoOpWhenBothDefaultAdminsAlreadyExist() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByUsername("albert")).thenReturn(true);
        when(repo.existsByUsername("oscar")).thenReturn(true);

        serviceWith(repo).ensureDefaultUsersExist();

        verify(repo, never()).save(any(User.class));
    }

    @Test
    void skipsASeedUserWhenItsEmailIsAlreadyTakenByAnUnrelatedAccount() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.existsByEmail("albert@localhost")).thenReturn(true);

        serviceWith(repo).ensureDefaultUsersExist();

        verify(repo, times(1)).save(any(User.class));
        verify(repo).save(argThat(u -> u.getUsername().equals("oscar")));
    }
}
