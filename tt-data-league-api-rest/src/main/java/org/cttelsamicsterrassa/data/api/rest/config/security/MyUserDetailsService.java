package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        user.getRoles().forEach(role -> authorities.add(
                new SimpleGrantedAuthority("ROLE_" + role.name())));
        user.getPermissions().forEach(permission -> authorities.add(
                new SimpleGrantedAuthority(permission.value())));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isActived())
                .authorities(authorities)
                .build();

    }
}
