package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** The effective polling settings of a source: its stored override, else the configured defaults. */
public final class PollingSettingsProvider {

    /** Effective settings with the override metadata; version 0 and null author/time when not overridden. */
    public record Effective(
            PipelineSource source, PollingSettings settings, boolean overridden, long version, String updatedBy,
            Instant updatedAt) {
    }

    private final PollPolicyRepository policies;
    private final PollingSettings defaults;

    public PollingSettingsProvider(PollPolicyRepository policies, PollingSettings defaults) {
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.defaults = Objects.requireNonNull(defaults, "defaults is required");
    }

    public PollingSettings settings(PipelineSource source) {
        return effective(source).settings();
    }

    public Effective effective(PipelineSource source) {
        Optional<PollingPolicySettings> stored = policies.find(Objects.requireNonNull(source, "source is required"));
        return stored
                .map(policy -> new Effective(source, policy.settings(), true, policy.version(), policy.updatedBy(),
                        policy.updatedAt()))
                .orElseGet(() -> new Effective(source, defaults, false, 0L, null, null));
    }

    public PollingSettings defaults() {
        return defaults;
    }
}
