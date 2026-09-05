package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

public interface ImportResourceProcessService {

    ImportProcessResult process(ImportResource importResource);

    /**
     * As {@link #process(ImportResource)}, additionally reporting progress while the import runs.
     * Implementations that cannot report progress may ignore {@code listener}; the default keeps the
     * existing synchronous behaviour for callers that do not supply one.
     */
    default ImportProcessResult process(ImportResource importResource, ImportProgressListener listener) {
        return process(importResource);
    }
}
