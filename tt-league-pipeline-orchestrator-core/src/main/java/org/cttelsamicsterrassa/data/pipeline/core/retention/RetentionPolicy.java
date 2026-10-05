package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.util.EnumMap;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;

/** One retention rule for every artifact kind. */
public record RetentionPolicy(Map<ArtifactKind, RetentionRule> rules) {

    public RetentionPolicy {
        if (rules == null) {
            throw new IllegalArgumentException("rules is required");
        }
        EnumMap<ArtifactKind, RetentionRule> copy = new EnumMap<>(ArtifactKind.class);
        copy.putAll(rules);
        for (ArtifactKind kind : ArtifactKind.values()) {
            if (copy.get(kind) == null) {
                throw new IllegalArgumentException("A retention rule is required for " + kind);
            }
        }
        rules = Map.copyOf(copy);
    }
}
