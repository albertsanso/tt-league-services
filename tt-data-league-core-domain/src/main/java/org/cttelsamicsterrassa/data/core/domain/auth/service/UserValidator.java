package org.cttelsamicsterrassa.data.core.domain.auth.service;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Named
public class UserValidator {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^A-Za-z0-9].*");

    public List<String> validateUsername(String username) {
        List<String> errors = new ArrayList<>();
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            errors.add("Username must contain 3-20 characters and use only letters, numbers or underscore");
        }
        return errors;
    }

    public List<String> validateEmail(String email) {
        List<String> errors = new ArrayList<>();
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("Email format is invalid");
        }
        return errors;
    }

    public List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.length() < 8) {
            errors.add("Password must have at least 8 characters");
            return errors;
        }

        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            errors.add("Password must include at least one uppercase character");
        }
        if (!LOWERCASE_PATTERN.matcher(password).matches()) {
            errors.add("Password must include at least one lowercase character");
        }
        if (!NUMBER_PATTERN.matcher(password).matches()) {
            errors.add("Password must include at least one number");
        }
        if (!SPECIAL_PATTERN.matcher(password).matches()) {
            errors.add("Password must include at least one special character");
        }

        return errors;
    }

    public void validateOrThrow(String username, String email, String password) {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateUsername(username));
        errors.addAll(validateEmail(email));
        errors.addAll(validatePassword(password));

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("; ", errors));
        }
    }
}
