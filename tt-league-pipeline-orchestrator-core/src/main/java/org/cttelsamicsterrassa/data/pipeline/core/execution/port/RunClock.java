package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.time.Duration;
import java.time.Instant;

/** Time and waiting; tests replace it so nothing really sleeps. */
public interface RunClock {

    Instant now();

    void sleep(Duration duration) throws InterruptedException;
}
