package org.cttelsamicsterrassa.data.api.rest.user;

import io.swagger.v3.oas.annotations.Operation;
import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.auth.user.find.FindUserByNameQuery;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@UserOpenAPIv1Controller
public class UserController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "Returns the user represented by the authenticated, non-expired JWT")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FindUserByNameQuery query = new FindUserByNameQuery(authentication.getName());
        DomainQueryResponse<User> queryResponse = queryBus.push(query);
        if (queryResponse.isSuccess()) {
            User user = queryResponse.getResponse();
            UserDto userDto = UserDto.fromObject(user);
            return ResponseEntity.ok(userDto);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }
}
