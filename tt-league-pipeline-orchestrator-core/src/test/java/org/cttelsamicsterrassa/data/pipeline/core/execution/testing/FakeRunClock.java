package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** {@code sleep} advances {@code now} and records the duration; nothing really waits. */
public class FakeRunClock implements RunClock {

    private Instant now;
    private boolean interruptOnSleep;

    public final List<Duration> sleeps = new ArrayList<>();

    public FakeRunClock(Instant start) {
        this.now = start;
    }

    public FakeRunClock() {
        this(Instant.parse("2026-10-04T10:00:00Z"));
    }

    @Override
    public synchronized Instant now() {
        return now;
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        synchronized (this) {
            sleeps.add(duration);
            if (interruptOnSleep) {
                throw new InterruptedException("test interruption");
            }
            now = now.plus(duration);
        }
    }

    public synchronized void interruptOnSleep() {
        this.interruptOnSleep = true;
    }

    public synchronized void advance(Duration duration) {
        now = now.plus(duration);
    }
}
