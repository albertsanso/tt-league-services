package org.cttelsamicsterrassa.data.core.repository.jpa.auth.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserFilter;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserPage;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.cttelsamicsterrassa.data.core.domain.auth.user.repository.UserRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper.UserJPAToUserMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.auth.mapper.UserToUserJPAMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class UserRepositoryJpa implements UserRepository {
    private final UserRepositoryHelper userRepositoryHelper;
    private final UserJPAToUserMapper userJPAToUserMapper;
    private final UserToUserJPAMapper userToUserJPAMapper;

    @Override
    public Optional<User> findById(UUID id) {
        return userRepositoryHelper.findById(id).map(userJPAToUserMapper);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepositoryHelper.findByUsername(username).map(userJPAToUserMapper);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepositoryHelper.findByEmail(email).map(userJPAToUserMapper);
    }

    @Override
    public List<User> findAll() {
        return userRepositoryHelper.findAll().stream()
                .map(userJPAToUserMapper)
                .toList();
    }

    @Override
    public UserPage findPage(UserFilter filter) {
        PageRequest pageable = PageRequest.of(
                filter.page(), filter.size(), Sort.by(Sort.Direction.ASC, "username"));
        Page<org.cttelsamicsterrassa.data.core.repository.jpa.auth.model.UserJPA> page =
                userRepositoryHelper.findByFilter(filter.search(), filter.active(), pageable);
        List<User> content = page.getContent().stream().map(userJPAToUserMapper).toList();
        return UserPage.of(content, page.getTotalElements(), filter.page(), filter.size());
    }

    @Override
    public long countActiveAdmins() {
        return userRepositoryHelper.countActiveByRole(UserRole.ADMIN);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepositoryHelper.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepositoryHelper.existsByEmail(email);
    }

    @Override
    public void save(User user) {
        userRepositoryHelper.save(userToUserJPAMapper.apply(user));
    }
}
