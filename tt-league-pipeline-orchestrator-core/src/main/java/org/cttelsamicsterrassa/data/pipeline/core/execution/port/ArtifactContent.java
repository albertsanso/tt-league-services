package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.io.InputStream;

/** A stored artifact that can be streamed again for every upload attempt. */
public interface ArtifactContent {

    long size();

    /** Opens a new stream; the caller closes it. */
    InputStream open();
}
