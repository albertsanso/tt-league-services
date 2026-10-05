package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStoreException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;

/**
 * The only purge path: deletes the files of expired artifacts and marks their rows purged. Rows are kept as history.
 * The file goes first, so a crash in between leaves an unpurged row whose file is gone; the next pass deletes again
 * (idempotent) and marks it.
 */
public final class ArtifactCleanup {

    private static final System.Logger LOG = System.getLogger(ArtifactCleanup.class.getName());

    private final ArtifactRetentionRepository retention;
    private final RunArtifactRepository artifactRows;
    private final ArtifactStore store;
    private final RetentionPolicy policy;
    private final RunClock clock;

    public ArtifactCleanup(
            ArtifactRetentionRepository retention,
            RunArtifactRepository artifactRows,
            ArtifactStore store,
            RetentionPolicy policy,
            RunClock clock) {
        this.retention = Objects.requireNonNull(retention, "retention is required");
        this.artifactRows = Objects.requireNonNull(artifactRows, "artifactRows is required");
        this.store = Objects.requireNonNull(store, "store is required");
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public CleanupOutcome run() {
        Set<String> expired = RetentionRules.expired(
                retention.findUnpurged(), retention.seasonsBySource(), policy, clock.now());
        List<String> purged = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        long bytes = 0;
        for (String key : expired) {
            long size = artifactRows.findByStorageKey(key).stream()
                    .filter(row -> !row.isPurged())
                    .mapToLong(RunArtifact::sizeBytes)
                    .max()
                    .orElse(0);
            try {
                store.delete(key);
            } catch (ArtifactStoreException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not delete expired artifact " + key, e);
                failed.add(key);
                continue;
            }
            artifactRows.markPurged(key, clock.now());
            purged.add(key);
            bytes += size;
        }
        return new CleanupOutcome(purged, bytes, failed);
    }
}
