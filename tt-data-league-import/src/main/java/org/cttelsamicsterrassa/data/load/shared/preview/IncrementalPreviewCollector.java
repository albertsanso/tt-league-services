package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewActaCounts;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewDuplicateFixtureId;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewScopeChanges;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates the per-fixture {@link FixturePreview}s of one source's preview run into the
 * incremental-upload classification (FEAT-00088): acta buckets, planned changes per scope, the
 * duplicated {@code id_partido}s, the pending-registration count and the projected jornada counts.
 *
 * <p>Strictly informational and read-only. The acta buckets follow the completeness classification,
 * so legacy actas without {@code acta_publicada} are covered. Navigator-skipped unresolved pending
 * fixtures never reach a processor, so the caller adds their count once via
 * {@link #addNavigatorUnresolved(long)}. The projection deduplicates a fixture repeated in the
 * snapshot (same {@code id_partido}, or same scope/round/team names) so it contributes its delta
 * once, the last dispatched copy winning - matching the write path where the second write sees the
 * first.</p>
 */
public final class IncrementalPreviewCollector {

    private final List<FixturePreview> previews = new ArrayList<>();
    private final Map<String, List<String>> locationsByFixtureId = new LinkedHashMap<>();
    private final Map<String, FixturePreview> lastStoredByFixtureKey = new LinkedHashMap<>();
    private long navigatorUnresolved;

    public void add(FixturePreview preview) {
        if (preview == null) {
            return;
        }
        previews.add(preview);
        String fixtureId = preview.sourceFixtureId();
        if (fixtureId != null) {
            String location = ActaPreviewValidationSupport.location(preview.location());
            if (location != null) {
                locationsByFixtureId.computeIfAbsent(fixtureId, key -> new ArrayList<>()).add(location);
            }
        }
        if (preview.change().isStoredChange()) {
            lastStoredByFixtureKey.put(dedupKey(preview), preview);
        }
    }

    /**
     * Adds the count of unresolved pending fixtures a navigator skipped and recorded on the run
     * context, so they are counted once even though they never reached a processor.
     */
    public void addNavigatorUnresolved(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative, was " + count);
        }
        navigatorUnresolved += count;
    }

    public PreviewActaCounts actaCounts() {
        long published = 0;
        long unpublished = 0;
        long partial = 0;
        long invalid = 0;
        long unresolved = navigatorUnresolved;
        for (FixturePreview preview : previews) {
            ActaClassification classification = preview.classification();
            if (classification == null) {
                continue;
            }
            switch (classification.completeness()) {
                case PLAYED -> published++;
                case PENDING -> {
                    if (classification.unresolvedPendingFixture()) {
                        unresolved++;
                    } else {
                        unpublished++;
                    }
                }
                case PARTIAL -> partial++;
                case INVALID -> invalid++;
            }
        }
        return new PreviewActaCounts(published, unpublished, partial, invalid, unresolved);
    }

    public List<PreviewScopeChanges> scopeChanges() {
        Map<ScopeKey, EnumMap<PreviewChange, Long>> countsByScope = new LinkedHashMap<>();
        for (FixturePreview preview : previews) {
            EnumMap<PreviewChange, Long> counts = countsByScope.computeIfAbsent(
                    new ScopeKey(preview.competition(), preview.groupNumber(), preview.phase()),
                    key -> new EnumMap<>(PreviewChange.class));
            counts.merge(preview.change(), 1L, Long::sum);
        }
        List<PreviewScopeChanges> changes = new ArrayList<>(countsByScope.size());
        for (Map.Entry<ScopeKey, EnumMap<PreviewChange, Long>> entry : countsByScope.entrySet()) {
            ScopeKey scope = entry.getKey();
            EnumMap<PreviewChange, Long> counts = entry.getValue();
            changes.add(new PreviewScopeChanges(scope.competition(), scope.groupNumber(), scope.phase(),
                    counts.getOrDefault(PreviewChange.NEW_SCHEDULED, 0L),
                    counts.getOrDefault(PreviewChange.NEW_PLAYED, 0L),
                    counts.getOrDefault(PreviewChange.UPGRADE, 0L),
                    counts.getOrDefault(PreviewChange.RESCHEDULE, 0L),
                    counts.getOrDefault(PreviewChange.UNCHANGED, 0L),
                    counts.getOrDefault(PreviewChange.PLAYED_KEPT, 0L),
                    counts.getOrDefault(PreviewChange.REGRESSION, 0L),
                    counts.getOrDefault(PreviewChange.INVALID_ON_PLAYED, 0L),
                    counts.getOrDefault(PreviewChange.IDENTITY_CONFLICT, 0L),
                    counts.getOrDefault(PreviewChange.NOT_STORED, 0L)));
        }
        return changes;
    }

    public long teamsPendingRegistration() {
        return previews.stream().filter(FixturePreview::teamsPendingRegistration).count();
    }

    public List<PreviewDuplicateFixtureId> duplicateFixtureIds() {
        List<PreviewDuplicateFixtureId> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : locationsByFixtureId.entrySet()) {
            if (entry.getValue().size() >= 2) {
                duplicates.add(new PreviewDuplicateFixtureId(entry.getKey(), entry.getValue()));
            }
        }
        return duplicates;
    }

    /** The fixtures whose {@code id_partido} and natural key disagree, to be surfaced as warnings. */
    public List<FixturePreview> identityConflicts() {
        return previews.stream()
                .filter(preview -> preview.change() == PreviewChange.IDENTITY_CONFLICT)
                .toList();
    }

    /**
     * Applies the projected per-round/per-status deltas of the deduplicated stored changes to the
     * stored counts: rows that drop to zero are removed and a negative count is an
     * {@link IllegalStateException}, never a silent clamp (FEAT-00088).
     */
    public List<RoundStatusCount> project(List<RoundStatusCount> storedCounts) {
        Map<DeltaKey, Long> combined = new LinkedHashMap<>();
        if (storedCounts != null) {
            for (RoundStatusCount count : storedCounts) {
                combined.merge(new DeltaKey(count.competition(), count.groupNumber(), count.phase(),
                        count.round(), count.status()), count.matches(), Long::sum);
            }
        }
        for (FixturePreview preview : lastStoredByFixtureKey.values()) {
            applyDelta(combined, preview);
        }
        List<RoundStatusCount> projected = new ArrayList<>(combined.size());
        for (Map.Entry<DeltaKey, Long> entry : combined.entrySet()) {
            long matches = entry.getValue();
            if (matches < 0) {
                throw new IllegalStateException(
                        "Projected a negative match count for " + entry.getKey() + ": " + matches);
            }
            if (matches > 0) {
                DeltaKey key = entry.getKey();
                projected.add(new RoundStatusCount(key.competition(), key.groupNumber(), key.phase(),
                        key.round(), key.status(), matches));
            }
        }
        return projected;
    }

    private static void applyDelta(Map<DeltaKey, Long> combined, FixturePreview preview) {
        switch (preview.change()) {
            case NEW_SCHEDULED -> combined.merge(
                    new DeltaKey(preview.competition(), preview.groupNumber(), preview.phase(),
                            preview.round(), MatchStatus.SCHEDULED), 1L, Long::sum);
            case NEW_PLAYED -> combined.merge(
                    new DeltaKey(preview.competition(), preview.groupNumber(), preview.phase(),
                            preview.round(), MatchStatus.PLAYED), 1L, Long::sum);
            case UPGRADE -> {
                Integer existingRound = preview.existingRound() != null ? preview.existingRound() : preview.round();
                combined.merge(new DeltaKey(preview.competition(), preview.groupNumber(), preview.phase(),
                        existingRound, MatchStatus.SCHEDULED), -1L, Long::sum);
                combined.merge(new DeltaKey(preview.competition(), preview.groupNumber(), preview.phase(),
                        preview.round(), MatchStatus.PLAYED), 1L, Long::sum);
            }
            default -> {
            }
        }
    }

    /**
     * The deduplication key of a stored change: the {@code id_partido} when present, else the
     * natural-key scope plus round and both team names.
     */
    private static String dedupKey(FixturePreview preview) {
        if (preview.sourceFixtureId() != null) {
            return "id:" + preview.sourceFixtureId();
        }
        return "nk:" + preview.competition() + '|' + preview.groupNumber() + '|' + preview.phase() + '|'
                + preview.round() + '|' + preview.homeTeamName() + '|' + preview.awayTeamName();
    }

    private record ScopeKey(String competition, Integer groupNumber, String phase) {
    }

    private record DeltaKey(String competition, Integer groupNumber, String phase, int round,
                            MatchStatus status) {
    }
}
