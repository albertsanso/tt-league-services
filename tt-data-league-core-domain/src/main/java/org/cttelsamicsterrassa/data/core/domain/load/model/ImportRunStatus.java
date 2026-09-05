package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.Set;

/**
 * Lifecycle of one asynchronous import run, from acceptance to a terminal outcome.
 */
public enum ImportRunStatus {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCESS("success"),
    EMPTY_RESULT("empty-result"),
    FAILURE("failure");

    private static final Set<ImportRunStatus> TERMINAL = Set.of(SUCCESS, EMPTY_RESULT, FAILURE);

    private final String value;

    ImportRunStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public static ImportRunStatus fromProcessStatus(ImportProcessStatus status) {
        return switch (status) {
            case SUCCESS -> SUCCESS;
            case EMPTY_RESULT -> EMPTY_RESULT;
            case FAILURE, LOADING -> FAILURE;
        };
    }
}
