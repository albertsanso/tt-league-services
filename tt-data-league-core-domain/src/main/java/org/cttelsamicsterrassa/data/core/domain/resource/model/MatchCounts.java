package org.cttelsamicsterrassa.data.core.domain.resource.model;

/**
 * Match counts declared by an import manifest over the actas it packages: {@code expected} actas, of which
 * {@code withResult} are published and {@code pending} are not.
 */
public record MatchCounts(long expected, long withResult, long pending) {

    public MatchCounts {
        if (expected < 0 || withResult < 0 || pending < 0) {
            throw new IllegalArgumentException("manifest.json matchCounts values must not be negative");
        }
        if (withResult + pending != expected) {
            throw new IllegalArgumentException(
                    "manifest.json matchCounts withResult + pending must equal expected");
        }
    }
}
