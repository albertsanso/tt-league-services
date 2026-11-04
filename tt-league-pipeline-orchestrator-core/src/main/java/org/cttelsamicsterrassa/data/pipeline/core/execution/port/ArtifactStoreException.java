package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

public class ArtifactStoreException extends RuntimeException {

    public ArtifactStoreException(String message) {
        super(message);
    }

    public ArtifactStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
