package org.cttelsamicsterrassa.data.pipeline.core.trigger.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import java.util.List;
import java.util.Optional;

/** At most one pending trigger per source. */
public interface PendingTriggerRepository {

    /** Throws {@link PendingTriggerExistsException} when the source already has one. */
    PendingTrigger add(PendingTrigger trigger);

    /** Atomic find-and-delete. */
    Optional<PendingTrigger> take(PipelineSource source);

    /** Oldest request first. */
    List<PendingTrigger> findAll();
}
