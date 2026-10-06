package org.cttelsamicsterrassa.data.pipeline.core.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.junit.jupiter.api.Test;

class ArtifactCleanupTest {

    private final FakeRunClock clock = new FakeRunClock();
    private final InMemoryRunArtifactRepository rows = new InMemoryRunArtifactRepository();
    private final InMemoryArtifactRetentionRepository retention = new InMemoryArtifactRetentionRepository(rows);
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final ArtifactCleanup cleanup = new ArtifactCleanup(retention, rows, store, policy(), clock);

    private static RetentionPolicy policy() {
        Map<ArtifactKind, RetentionRule> rules = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            rules.put(kind, new RetentionRule.MaxAge(Duration.ofDays(1)));
        }
        return new RetentionPolicy(rules);
    }

    private String oldArtifact(String key, byte[] bytes, boolean activeRun) {
        UUID runId = UUID.randomUUID();
        var stored = store.store(key, new ByteArrayInputStream(bytes));
        rows.add(new RunArtifact(UUID.randomUUID(), runId, UUID.randomUUID(), ArtifactKind.ZIP, key, stored.sha256(),
                stored.sizeBytes(), clock.now().minus(Duration.ofDays(3))));
        retention.run(runId, PipelineSource.RFETM, "2025-2026", activeRun);
        return key;
    }

    @Test
    void deletesTheFileAndMarksTheRowButKeepsIt() {
        oldArtifact("a.zip", new byte[] {1, 2, 3}, false);

        CleanupOutcome outcome = cleanup.run();

        assertThat(outcome.purgedKeys()).containsExactly("a.zip");
        assertThat(outcome.purgedBytes()).isEqualTo(3);
        assertThat(outcome.failedKeys()).isEmpty();
        assertThat(store.exists("a.zip")).isFalse();
        assertThat(rows.all()).singleElement().satisfies(row -> {
            assertThat(row.isPurged()).isTrue();
            assertThat(row.purgedAt()).isEqualTo(clock.now());
        });
    }

    @Test
    void aFailedDeleteIsCountedAndLeavesTheKeyUnpurgedWhileOthersContinue() {
        oldArtifact("bad.zip", new byte[] {1}, false);
        store.failDeletes(true);

        CleanupOutcome failed = cleanup.run();

        assertThat(failed.failedKeys()).containsExactly("bad.zip");
        assertThat(failed.purgedKeys()).isEmpty();
        assertThat(rows.all()).noneMatch(RunArtifact::isPurged);
        assertThat(store.exists("bad.zip")).isTrue();

        store.failDeletes(false);
        assertThat(cleanup.run().purgedKeys()).containsExactly("bad.zip");
    }

    @Test
    void rerunAfterACrashBetweenDeleteAndMarkMarksTheRow() {
        oldArtifact("crash.zip", new byte[] {1}, false);
        store.delete("crash.zip");

        CleanupOutcome outcome = cleanup.run();

        assertThat(outcome.purgedKeys()).containsExactly("crash.zip");
        assertThat(rows.all()).allMatch(RunArtifact::isPurged);
    }

    @Test
    void alreadyPurgedRowsAndActiveRunsAreIgnored() {
        oldArtifact("done.zip", new byte[] {1}, false);
        assertThat(cleanup.run().purgedKeys()).containsExactly("done.zip");
        oldArtifact("busy.zip", new byte[] {1}, true);

        CleanupOutcome second = cleanup.run();

        assertThat(second.purgedKeys()).isEmpty();
        assertThat(store.exists("busy.zip")).isTrue();
    }
}
