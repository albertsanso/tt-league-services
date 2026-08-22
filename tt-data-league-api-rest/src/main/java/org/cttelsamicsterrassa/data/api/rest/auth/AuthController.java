package org.cttelsamicsterrassa.data.api.rest.auth;

import org.albertsanso.commons.command.CommandBus;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.api.rest.config.security.JwtService;
import org.cttelsamicsterrassa.data.api.rest.config.security.TokenBlacklistService;
import org.cttelsamicsterrassa.data.core.application.auth.user.login.AuthenticateUserQuery;
import org.cttelsamicsterrassa.data.core.application.auth.user.register.RegisterUserCommand;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.User;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.InvalidCredentialsException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAlreadyExistsException;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserAuthenticationService;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@AuthOpenAPIv1Controller
public class AuthController {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserAuthenticationService userAuthenticationService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            RegisterUserCommand registerUserCommand = new RegisterUserCommand(
                    request.username(),
                    request.email(),
                    request.password());

            DomainCommandResponse commandResponse = commandBus.push(registerUserCommand);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(commandResponse.getResponse());
        }
        catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorMessage(e.getMessage()));
        }
        catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage(e.getMessage()));
        }
        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorMessage("Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthenticateUserQuery authenticateUserQuery = new AuthenticateUserQuery(
                    request.username(),
                    request.password());
            DomainQueryResponse<User> queryResponse = queryBus.push(authenticateUserQuery);

            if (queryResponse.isSuccess()) {
                User authedUser = queryResponse.getResponse();
                String token = jwtService.generateToken(authedUser.getUsername(), null);
                return ResponseEntity.ok(LoginResponse.createForBearer(token, authedUser.getUsername()));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorMessage("Invalid username or password"));
            }
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorMessage("Invalid username or password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorMessage("Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                tokenBlacklistService.blacklistToken(token, jwtService.extractExpiration(token));
            } catch (Exception e) {
                // invalid token — nothing to blacklist
            }
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    static record ErrorMessage(String message) {
    }
}
