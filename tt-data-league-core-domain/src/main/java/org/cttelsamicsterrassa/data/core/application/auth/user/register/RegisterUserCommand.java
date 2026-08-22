package org.cttelsamicsterrassa.data.core.application.auth.user.register;

import org.albertsanso.commons.command.DomainCommand;

import java.time.ZonedDateTime;
import java.util.UUID;

public class RegisterUserCommand extends DomainCommand {
    private final String username;
    private final String email;
    private final String plainPassword;

    public RegisterUserCommand(String username, String email, String plainPassword) {
        super(ZonedDateTime.now(), UUID.randomUUID().toString());
        this.username = username;
        this.email = email;
        this.plainPassword = plainPassword;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPlainPassword() {
        return plainPassword;
    }
}
