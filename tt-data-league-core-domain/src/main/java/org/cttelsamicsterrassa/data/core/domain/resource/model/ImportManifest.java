package org.cttelsamicsterrassa.data.core.domain.resource.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record ImportManifest(
        String source,
        List<String> seasons,
        Map<String, List<String>> assets,
        Path extractionFolder,
        UploadMode mode,
        ManifestProvenance provenance) {

    public ImportManifest(String source,
                          List<String> seasons,
                          Map<String, List<String>> assets,
                          Path extractionFolder) {
        this(source, seasons, assets, extractionFolder, UploadMode.SNAPSHOT);
    }

    public ImportManifest(String source,
                          List<String> seasons,
                          Map<String, List<String>> assets,
                          Path extractionFolder,
                          UploadMode mode) {
        this(source, seasons, assets, extractionFolder, mode, ManifestProvenance.EMPTY);
    }

    public ImportManifest {
        seasons = List.copyOf(seasons);
        assets = assets.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
        mode = Objects.requireNonNull(mode, "mode");
        provenance = Objects.requireNonNull(provenance, "provenance");
    }
}
