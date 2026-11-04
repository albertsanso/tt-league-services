package org.cttelsamicsterrassa.data.pipeline.core.alert.port;

import org.cttelsamicsterrassa.data.pipeline.core.alert.Alert;
import java.util.List;

/** Persistence port of the alerts; the evaluator is the only writer. */
public interface AlertRepository {

    /** Uncleared alerts, oldest first ({@code raisedAt}, then {@code id}). */
    List<Alert> findActive();

    /**
     * Inserts a new alert and returns it. Throws {@link ActiveAlertExistsException} when an uncleared alert with the
     * same kind and condition key exists.
     */
    Alert raise(Alert alert);

    /**
     * Persists a change and returns the alert with the incremented version. Throws {@link StaleAlertException} when
     * the stored version differs from {@code alert.version()} and {@link IllegalArgumentException} for an unknown id.
     */
    Alert update(Alert alert);
}
