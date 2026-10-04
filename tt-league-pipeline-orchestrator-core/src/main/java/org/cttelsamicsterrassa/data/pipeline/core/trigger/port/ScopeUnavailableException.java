package org.cttelsamicsterrassa.data.pipeline.core.trigger.port;

/** The open-match-day scope cannot be produced; the message is user-facing and holds no secrets. */
public class ScopeUnavailableException extends RuntimeException {

    public static final String SCOPE_UNAVAILABLE = "SCOPE_UNAVAILABLE";
    public static final String NO_OPEN_MATCH_DAYS = "NO_OPEN_MATCH_DAYS";

    private final String code;

    public ScopeUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
