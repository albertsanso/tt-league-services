package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

public enum ImportResourceStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    ERROR;

    public static List<ImportResourceStatus> getAllFinishedStatuses() {
        return List.of(PROCESSED, ERROR);
    }
}
