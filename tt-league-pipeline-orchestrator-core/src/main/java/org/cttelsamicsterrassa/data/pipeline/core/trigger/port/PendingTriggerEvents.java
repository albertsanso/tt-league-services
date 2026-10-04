package org.cttelsamicsterrassa.data.pipeline.core.trigger.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;

/** Hook for pending-trigger changes; implementations must not throw into the caller. */
public interface PendingTriggerEvents {

    default void queued(PendingTrigger trigger) {
    }

    default void launched(PendingTrigger trigger, PipelineRun run) {
    }

    default void dropped(PendingTrigger trigger, String code) {
    }

    static PendingTriggerEvents none() {
        return new PendingTriggerEvents() {
        };
    }
}
