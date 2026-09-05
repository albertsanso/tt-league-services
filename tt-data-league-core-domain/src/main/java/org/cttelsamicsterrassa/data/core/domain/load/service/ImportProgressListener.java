package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;

/**
 * Receives monotonic progress snapshots while an import runs. Implementations must be cheap and
 * must never change import success/failure semantics; they only observe progress.
 */
@FunctionalInterface
public interface ImportProgressListener {

    void onProgress(ImportRunProgress progress);

    static ImportProgressListener noop() {
        return progress -> {
        };
    }
}
