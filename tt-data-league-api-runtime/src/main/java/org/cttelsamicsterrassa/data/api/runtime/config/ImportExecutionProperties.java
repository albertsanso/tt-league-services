package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tt.league.import.execution")
public class ImportExecutionProperties {
    private int batchSize = 50;
    private String clubConsolidation = "disabled";
    private String playerConsolidation = "disabled";
    private String amendedActaDetection = "disabled";

    /**
     * The RFETM teams folder is not bound here: it is resolved at call time from the persisted
     * {@code IMPORT/rfetm-teams-folder} administrator setting (see {@code ImportExecutionConfiguration}),
     * so it is left {@code null} in these static options.
     */
    public ImportExecutionOptions toOptions() {
        return new ImportExecutionOptions(mode(clubConsolidation), mode(playerConsolidation),
                null, batchSize, amendedActaMode(amendedActaDetection));
    }

    private static ConsolidationMode mode(String value) {
        if (value == null || value.isBlank() || "disabled".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)) {
            return null;
        }
        return ConsolidationMode.valueOf(value.trim().toUpperCase());
    }

    private static AmendedActaMode amendedActaMode(String value) {
        if (value == null || value.isBlank() || "disabled".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)) {
            return null;
        }
        return AmendedActaMode.valueOf(value.trim().toUpperCase());
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setClubConsolidation(String clubConsolidation) {
        this.clubConsolidation = clubConsolidation;
    }

    public void setPlayerConsolidation(String playerConsolidation) {
        this.playerConsolidation = playerConsolidation;
    }

    public void setAmendedActaDetection(String amendedActaDetection) {
        this.amendedActaDetection = amendedActaDetection;
    }
}
