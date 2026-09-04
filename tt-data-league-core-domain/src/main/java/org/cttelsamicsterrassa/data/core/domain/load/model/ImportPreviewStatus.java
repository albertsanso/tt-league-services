package org.cttelsamicsterrassa.data.core.domain.load.model;

public enum ImportPreviewStatus {
    LOADING("loading"),
    SUCCESS("success"),
    EMPTY_RESULT("empty-result"),
    FAILURE("failure");

    private final String value;

    ImportPreviewStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
