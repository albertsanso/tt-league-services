package org.cttelsamicsterrassa.data.api.rest.user;

import io.swagger.v3.oas.annotations.Operation;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@UserOpenAPIv1Controller
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "Returns the user represented by the authenticated, non-expired JWT")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userService.getUserByUsername(authentication.getName()).orElse(null);
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok(UserDto.fromObject(user));
    }
}
