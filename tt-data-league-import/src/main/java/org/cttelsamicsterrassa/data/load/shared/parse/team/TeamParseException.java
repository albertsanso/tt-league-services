package org.cttelsamicsterrassa.data.load.shared.parse.team;

import java.nio.file.Path;

/**
 * Thrown when a team file cannot be read or does not match the team schema.
 */
public class TeamParseException extends RuntimeException {

    private final transient Path file;

    public TeamParseException(Path file, Throwable cause) {
        super("Cannot parse team file %s: %s".formatted(file, cause.getMessage()), cause);
        this.file = file;
    }

    public Path getFile() {
        return file;
    }
}
