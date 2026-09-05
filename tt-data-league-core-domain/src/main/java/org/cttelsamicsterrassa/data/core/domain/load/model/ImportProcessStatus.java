package org.cttelsamicsterrassa.data.core.domain.load.model;

public enum ImportProcessStatus {
    LOADING("loading"),
    SUCCESS("success"),
    EMPTY_RESULT("empty-result"),
    FAILURE("failure");

    private final String value;

    ImportProcessStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
