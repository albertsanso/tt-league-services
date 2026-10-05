package org.cttelsamicsterrassa.data.pipeline.runtime.logging;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Binds the orchestrator run id to the {@value #RUN_ID} MDC key so every log line written on the thread carries it,
 * including the core's {@code System.Logger} lines through the JUL bridge. This is the only class that writes the key.
 */
public final class RunLogContext {

    public static final String RUN_ID = "runId";

    private RunLogContext() {}

    /** Binds the run id until the returned scope is closed; the previous value (if any) is then restored. */
    public static Scope bind(UUID runId) {
        Objects.requireNonNull(runId, "runId is required");
        String previous = MDC.get(RUN_ID);
        MDC.put(RUN_ID, runId.toString());
        return new Scope(previous);
    }

    /** Restores the run id that was bound before {@link #bind}. */
    public static final class Scope implements AutoCloseable {

        private final String previous;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(RUN_ID);
            } else {
                MDC.put(RUN_ID, previous);
            }
        }
    }
}
