package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import java.time.Duration;
import java.time.Instant;

public final class SystemRunClock implements RunClock {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }
}
