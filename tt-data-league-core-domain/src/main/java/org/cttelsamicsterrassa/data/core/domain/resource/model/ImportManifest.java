package org.cttelsamicsterrassa.data.core.domain.resource.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ImportManifest(
        String source,
        List<String> seasons,
        Map<String, List<String>> assets,
        Path extractionFolder) {

    public ImportManifest {
        seasons = List.copyOf(seasons);
        assets = assets.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }
}
