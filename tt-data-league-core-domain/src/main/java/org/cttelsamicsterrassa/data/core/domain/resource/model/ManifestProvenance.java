package org.cttelsamicsterrassa.data.core.domain.resource.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Optional provenance declared by an import manifest: the producing run, the generator and its version, the
 * content hash of the ZIP entries and the match counts of the packaged actas.
 */
public record ManifestProvenance(
        Optional<String> runId,
        Optional<String> generator,
        Optional<String> generatorVersion,
        Optional<String> contentSha256,
        Optional<MatchCounts> matchCounts) {

    public static final ManifestProvenance EMPTY = new ManifestProvenance(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public ManifestProvenance {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(generatorVersion, "generatorVersion");
        Objects.requireNonNull(contentSha256, "contentSha256");
        Objects.requireNonNull(matchCounts, "matchCounts");
    }
}
