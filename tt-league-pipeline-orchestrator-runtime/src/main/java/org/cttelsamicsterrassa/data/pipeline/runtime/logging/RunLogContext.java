package org.cttelsamicsterrassa.data.pipeline.runtime.logging;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Binds the orchestrator run id to the {@value #RUN_ID} MDC key so every log line written on the thread carries it,
 * including the core's {@code System.Logger} lines through the JUL bridge. This is the only class that writes the keys ({@code runId} and {@code unitKey}).
 */
public final class RunLogContext {

    public static final String RUN_ID = "runId";
    public static final String UNIT_KEY = "unitKey";

    private RunLogContext() {}

    /** Binds the run id until the returned scope is closed; the previous value (if any) is then restored. */
    public static Scope bind(UUID runId) {
        Objects.requireNonNull(runId, "runId is required");
        String previous = MDC.get(RUN_ID);
        MDC.put(RUN_ID, runId.toString());
        return new Scope(previous);
    }

    /**
     * Binds the run id and the unit key until the returned scope is closed. The unit key is an identifier of an ingest
     * group (a hash), so it is safe in logs; it is never a metric tag.
     */
    public static Scope bind(UUID runId, String unitKey) {
        Objects.requireNonNull(unitKey, "unitKey is required");
        Scope scope = bind(runId);
        String previousUnit = MDC.get(UNIT_KEY);
        MDC.put(UNIT_KEY, unitKey);
        return new Scope(scope.previous, previousUnit, true);
    }

    /** Restores the values that were bound before {@link #bind}. */
    public static final class Scope implements AutoCloseable {

        private final String previous;
        private final String previousUnit;
        private final boolean unitBound;

        private Scope(String previous) {
            this(previous, null, false);
        }

        private Scope(String previous, String previousUnit, boolean unitBound) {
            this.previous = previous;
            this.previousUnit = previousUnit;
            this.unitBound = unitBound;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(RUN_ID);
            } else {
                MDC.put(RUN_ID, previous);
            }
            if (unitBound) {
                if (previousUnit == null) {
                    MDC.remove(UNIT_KEY);
                } else {
                    MDC.put(UNIT_KEY, previousUnit);
                }
            }
        }
    }
}
