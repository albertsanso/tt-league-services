package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ImportExecutionRequest(ImportSource source, Path actasFolder, Optional<Season> season) {
    public ImportExecutionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(actasFolder, "actasFolder");
        season = season == null ? Optional.empty() : season;
    }
}
