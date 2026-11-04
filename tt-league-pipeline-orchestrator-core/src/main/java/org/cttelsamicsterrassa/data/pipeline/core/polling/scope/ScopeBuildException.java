package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

/** The open match days cannot be mapped to ingest scopes; the message is user-facing and holds no secrets. */
public class ScopeBuildException extends RuntimeException {

    public static final String SCOPE_UNMATCHED = "SCOPE_UNMATCHED";

    private final String code;

    public ScopeBuildException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
