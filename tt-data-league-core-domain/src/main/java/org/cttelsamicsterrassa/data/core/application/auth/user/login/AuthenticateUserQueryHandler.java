package org.cttelsamicsterrassa.data.core.application.auth.user.login;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAuthenticationService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class AuthenticateUserQueryHandler extends DomainQueryHandler<AuthenticateUserQuery, User> {

    private final UserAuthenticationService userAuthenticationService;

    @Inject
    public AuthenticateUserQueryHandler(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Override
    public DomainQueryResponse<User> handle(AuthenticateUserQuery query) {
        return userAuthenticationService.authenticateUser(query.getUsername(), query.getPassword())
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }
}
