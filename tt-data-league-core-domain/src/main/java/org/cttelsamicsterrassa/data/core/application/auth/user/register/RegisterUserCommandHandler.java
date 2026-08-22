package org.cttelsamicsterrassa.data.core.application.auth.user.register;

import org.albertsanso.commons.command.DomainCommandHandler;
import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.UserRegistrationService;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class RegisterUserCommandHandler extends DomainCommandHandler<RegisterUserCommand> {

    private final UserRegistrationService userRegistrationService;

    @Inject
    public RegisterUserCommandHandler(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @Override
    public DomainCommandResponse handle(RegisterUserCommand command) {
        return DomainCommandResponse.successResponse(
                userRegistrationService.registerUser(
                        command.getUsername(), command.getEmail(), command.getPlainPassword()));
    }
}
