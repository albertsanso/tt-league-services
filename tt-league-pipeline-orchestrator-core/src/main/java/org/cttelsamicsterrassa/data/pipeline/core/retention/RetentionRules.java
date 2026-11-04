package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** The only place that decides what expires. Pure: no I/O and no clock. */
public final class RetentionRules {

    private RetentionRules() {
    }

    /**
     * Returns the storage keys to purge. Rows are grouped by key (replay rows share the original's key); the oldest
     * row is the original and gives the age, source and season. A key referenced by an active run never expires, and
     * a season unknown to {@code seasonsBySource} is kept.
     */
    public static Set<String> expired(
            List<RetainedArtifact> artifacts,
            Map<PipelineSource, List<String>> seasonsBySource,
            RetentionPolicy policy,
            Instant now) {
        Map<String, List<RetainedArtifact>> byKey = new LinkedHashMap<>();
        for (RetainedArtifact retained : artifacts) {
            byKey.computeIfAbsent(retained.artifact().storageKey(), key -> new ArrayList<>()).add(retained);
        }
        Set<String> expired = new LinkedHashSet<>();
        byKey.forEach((key, rows) -> {
            if (rows.stream().anyMatch(RetainedArtifact::runActive)) {
                return;
            }
            RetainedArtifact original = rows.stream()
                    .min(Comparator.comparing(row -> row.artifact().createdAt()))
                    .orElseThrow();
            RetentionRule rule = policy.rules().get(original.artifact().kind());
            if (isExpired(rule, original, seasonsBySource, now)) {
                expired.add(key);
            }
        });
        return expired;
    }

    private static boolean isExpired(
            RetentionRule rule,
            RetainedArtifact original,
            Map<PipelineSource, List<String>> seasonsBySource,
            Instant now) {
        return switch (rule) {
            case RetentionRule.MaxAge maxAge ->
                    !original.artifact().createdAt().plus(maxAge.duration()).isAfter(now);
            case RetentionRule.Seasons seasons -> {
                List<String> known = seasonsBySource.getOrDefault(original.source(), List.of());
                if (!known.contains(original.season())) {
                    yield false;
                }
                List<String> newest = known.stream()
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .limit(seasons.count())
                        .toList();
                yield !newest.contains(original.season());
            }
        };
    }
}
