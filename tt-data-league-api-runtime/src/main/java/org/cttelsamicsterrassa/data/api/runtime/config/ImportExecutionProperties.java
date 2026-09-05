package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "tt.league.import.execution")
public class ImportExecutionProperties {
    private int batchSize = 50;
    private String clubConsolidation = "disabled";
    private String playerConsolidation = "disabled";
    private Path rfetmTeamsFolder;

    public ImportExecutionOptions toOptions() {
        return new ImportExecutionOptions(mode(clubConsolidation), mode(playerConsolidation),
                rfetmTeamsFolder, batchSize);
    }

    private static ConsolidationMode mode(String value) {
        if (value == null || value.isBlank() || "disabled".equalsIgnoreCase(value)
                || "none".equalsIgnoreCase(value)) {
            return null;
        }
        return ConsolidationMode.valueOf(value.trim().toUpperCase());
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

    public void setRfetmTeamsFolder(Path rfetmTeamsFolder) {
        this.rfetmTeamsFolder = rfetmTeamsFolder;
    }
}
