package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.io.InputStream;

/** Receives the package body while it streams in. */
@FunctionalInterface
public interface PackageSink {

    StoredArtifact write(InputStream body);
}
