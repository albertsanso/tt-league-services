package org.cttelsamicsterrassa.data.core.application.auth.user.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FindUserByNameQueryHandler extends DomainQueryHandler<FindUserByNameQuery, User> {

    private final UserService userService;

    @Inject
    public FindUserByNameQueryHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public DomainQueryResponse<User> handle(FindUserByNameQuery findUserByNameQuery) {
        return userService.getUserByUsername(findUserByNameQuery.getUsername())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
