package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingPolicySettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollPolicyException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Version-checked in-memory store of the polling policy overrides. */
public class InMemoryPollPolicyRepository implements PollPolicyRepository {

    private final Map<PipelineSource, PollingPolicySettings> policies = new EnumMap<>(PipelineSource.class);

    @Override
    public synchronized Optional<PollingPolicySettings> find(PipelineSource source) {
        return Optional.ofNullable(policies.get(source));
    }

    @Override
    public synchronized List<PollingPolicySettings> findAll() {
        return List.copyOf(policies.values());
    }

    @Override
    public synchronized PollingPolicySettings save(
            PipelineSource source, PollingSettings settings, String updatedBy, Instant updatedAt,
            long expectedVersion) {
        PollingPolicySettings stored = policies.get(source);
        long storedVersion = stored == null ? 0L : stored.version();
        if (storedVersion != expectedVersion) {
            throw new StalePollPolicyException(source, expectedVersion);
        }
        PollingPolicySettings next =
                new PollingPolicySettings(source, settings, expectedVersion + 1, updatedBy, updatedAt);
        policies.put(source, next);
        return next;
    }

    @Override
    public synchronized boolean delete(PipelineSource source) {
        return policies.remove(source) != null;
    }
}
