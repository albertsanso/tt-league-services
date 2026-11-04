package org.cttelsamicsterrassa.data.core.domain.load.model;

/**
 * How an import preview bucketed the actas of one snapshot (FEAT-00088): {@code published} counts
 * PLAYED actas, {@code unpublished} counts PENDING actas with resolvable teams, {@code partial} and
 * {@code invalid} count the respective reported actas, and {@code unresolved} counts pending
 * fixtures without team names that no natural key can identify. Buckets follow the completeness
 * classification, so legacy actas without {@code acta_publicada} are covered. Informational only.
 */
public record PreviewActaCounts(
        long published,
        long unpublished,
        long partial,
        long invalid,
        long unresolved) {

    public PreviewActaCounts {
        requireNonNegative(published, "published");
        requireNonNegative(unpublished, "unpublished");
        requireNonNegative(partial, "partial");
        requireNonNegative(invalid, "invalid");
        requireNonNegative(unresolved, "unresolved");
    }

    public static PreviewActaCounts empty() {
        return new PreviewActaCounts(0, 0, 0, 0, 0);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }
}
