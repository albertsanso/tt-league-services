package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;

import java.nio.file.Path;

public record ImportExecutionOptions(
        ConsolidationMode clubConsolidationMode,
        ConsolidationMode playerConsolidationMode,
        Path rfetmTeamsFolder,
        int batchSize,
        AmendedActaMode amendedActaMode) {
    public static ImportExecutionOptions defaults() {
        return new ImportExecutionOptions(null, null, null, 50, null);
    }

    public ImportExecutionOptions {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
