package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertKind;

/** Another writer already holds an uncleared alert with the same kind and condition key. */
public class ActiveAlertExistsException extends RuntimeException {

    public ActiveAlertExistsException(AlertKind kind, String conditionKey) {
        super("An active " + kind + " alert already exists for " + conditionKey);
    }

    public ActiveAlertExistsException(AlertKind kind, String conditionKey, Throwable cause) {
        super("An active " + kind + " alert already exists for " + conditionKey, cause);
    }
}
