package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingPolicySettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Persistence port of the per-source polling policy overrides. */
public interface PollPolicyRepository {

    Optional<PollingPolicySettings> find(PipelineSource source);

    List<PollingPolicySettings> findAll();

    /**
     * Stores the override. {@code expectedVersion} 0 creates it (it must not exist); a higher value must equal the
     * stored version. The result carries {@code expectedVersion + 1}. Throws {@link StalePollPolicyException} otherwise.
     */
    PollingPolicySettings save(
            PipelineSource source, PollingSettings settings, String updatedBy, Instant updatedAt,
            long expectedVersion);

    /** Removes the override; returns whether one existed. */
    boolean delete(PipelineSource source);
}
