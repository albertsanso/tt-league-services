package org.cttelsamicsterrassa.data.load.shared.parse.acta;

import java.nio.file.Path;

/**
 * Thrown when a match report file cannot be read or does not match the acta schema.
 */
public class ActaParseException extends RuntimeException {

    private final transient Path file;

    public ActaParseException(Path file, Throwable cause) {
        super("Cannot parse match report %s: %s".formatted(file, cause.getMessage()), cause);
        this.file = file;
    }

    public Path getFile() {
        return file;
    }
}
