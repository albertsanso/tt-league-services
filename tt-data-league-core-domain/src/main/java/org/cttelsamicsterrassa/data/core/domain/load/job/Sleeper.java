package org.cttelsamicsterrassa.data.core.domain.load.job;

import java.time.Duration;

/**
 * Pauses the calling thread; replaced in tests so busy waits run instantly.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());

    void sleep(Duration duration) throws InterruptedException;
}
