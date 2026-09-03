package org.cttelsamicsterrassa.data.core.domain.resource.model;

import java.nio.file.Path;
import java.util.List;

public record ImportManifest(
        String source,
        List<String> seasons,
        List<String> files,
        Path extractionFolder,
        String assetType) {
}
