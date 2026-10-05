package org.cttelsamicsterrassa.data.pipeline.core.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.junit.jupiter.api.Test;

class RetentionRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final String SHA = "a".repeat(64);

    private static RetentionPolicy policy(RetentionRule zip, RetentionRule other) {
        Map<ArtifactKind, RetentionRule> rules = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            rules.put(kind, kind == ArtifactKind.ZIP ? zip : other);
        }
        return new RetentionPolicy(rules);
    }

    private static RetainedArtifact retained(
            String key, ArtifactKind kind, Instant createdAt, PipelineSource source, String season, boolean active) {
        return new RetainedArtifact(
                new RunArtifact(UUID.randomUUID(), UUID.randomUUID(), kind, key, SHA, 1, createdAt), source, season,
                active);
    }

    private static final Map<PipelineSource, List<String>> SEASONS = Map.of(
            PipelineSource.RFETM, List.of("2024-2025", "2025-2026"),
            PipelineSource.FCTT, List.of("2025-2026"));

    @Test
    void maxAgeExpiresAtTheBoundaryAndNotBefore() {
        RetentionPolicy policy = policy(new RetentionRule.MaxAge(Duration.ofDays(90)), new RetentionRule.Seasons(1));
        Instant boundary = NOW.minus(Duration.ofDays(90));

        var expired = RetentionRules.expired(List.of(
                retained("at-boundary", ArtifactKind.ZIP, boundary, PipelineSource.RFETM, "2025-2026", false),
                retained("just-inside", ArtifactKind.ZIP, boundary.plusSeconds(1), PipelineSource.RFETM,
                        "2025-2026", false)), SEASONS, policy, NOW);

        assertThat(expired).containsExactly("at-boundary");
    }

    @Test
    void seasonsKeepsTheNewestNPerSourceIndependently() {
        RetentionPolicy policy = policy(new RetentionRule.Seasons(1), new RetentionRule.Seasons(1));

        var expired = RetentionRules.expired(List.of(
                retained("rfetm-old", ArtifactKind.ZIP, NOW, PipelineSource.RFETM, "2024-2025", false),
                retained("rfetm-new", ArtifactKind.ZIP, NOW, PipelineSource.RFETM, "2025-2026", false),
                retained("fctt-only", ArtifactKind.ZIP, NOW, PipelineSource.FCTT, "2025-2026", false)),
                SEASONS, policy, NOW);

        assertThat(expired).containsExactly("rfetm-old");
        assertThat(RetentionRules.expired(List.of(
                retained("rfetm-old", ArtifactKind.ZIP, NOW, PipelineSource.RFETM, "2024-2025", false)),
                SEASONS, policy(new RetentionRule.Seasons(2), new RetentionRule.Seasons(2)), NOW)).isEmpty();
    }

    @Test
    void aSeasonUnknownForTheSourceIsKept() {
        var expired = RetentionRules.expired(List.of(
                retained("k", ArtifactKind.ZIP, NOW, PipelineSource.BCNESA, "2020-2021", false)),
                SEASONS, policy(new RetentionRule.Seasons(1), new RetentionRule.Seasons(1)), NOW);

        assertThat(expired).isEmpty();
    }

    @Test
    void sharedKeysAreAgedByTheOldestRow() {
        RetentionPolicy policy = policy(new RetentionRule.MaxAge(Duration.ofDays(10)), new RetentionRule.Seasons(1));
        Instant original = NOW.minus(Duration.ofDays(30));

        var expired = RetentionRules.expired(List.of(
                retained("shared", ArtifactKind.ZIP, NOW.minus(Duration.ofDays(1)), PipelineSource.RFETM,
                        "2025-2026", false),
                retained("shared", ArtifactKind.ZIP, original, PipelineSource.RFETM, "2025-2026", false)),
                SEASONS, policy, NOW);

        assertThat(expired).containsExactly("shared");
    }

    @Test
    void aKeyReferencedByAnActiveRunNeverExpires() {
        RetentionPolicy policy = policy(new RetentionRule.MaxAge(Duration.ofDays(1)), new RetentionRule.Seasons(1));
        Instant old = NOW.minus(Duration.ofDays(30));

        var expired = RetentionRules.expired(List.of(
                retained("shared", ArtifactKind.ZIP, old, PipelineSource.RFETM, "2025-2026", false),
                retained("shared", ArtifactKind.ZIP, NOW, PipelineSource.RFETM, "2025-2026", true)),
                SEASONS, policy, NOW);

        assertThat(expired).isEmpty();
    }

    @Test
    void eachKindUsesItsOwnRule() {
        RetentionPolicy policy = policy(new RetentionRule.Seasons(2), new RetentionRule.MaxAge(Duration.ofDays(1)));
        Instant old = NOW.minus(Duration.ofDays(5));

        var expired = RetentionRules.expired(List.of(
                retained("zip", ArtifactKind.ZIP, old, PipelineSource.RFETM, "2025-2026", false),
                retained("raw", ArtifactKind.RAW, old, PipelineSource.RFETM, "2025-2026", false)),
                SEASONS, policy, NOW);

        assertThat(expired).containsExactly("raw");
    }

    @Test
    void emptyInputExpiresNothing() {
        assertThat(RetentionRules.expired(List.of(), Map.of(),
                policy(new RetentionRule.Seasons(1), new RetentionRule.Seasons(1)), NOW)).isEmpty();
    }

    @Test
    void valuesRejectInvalidInput() {
        assertThatThrownBy(() -> new RetentionRule.MaxAge(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionRule.Seasons(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionRule.Seasons(11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionPolicy(Map.of(ArtifactKind.ZIP, new RetentionRule.Seasons(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANIFEST");
    }
}
