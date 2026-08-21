package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubConsolidationProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

@Component
public class RfetmClubConsolidationRunner {

    private final RfetmClubConsolidationProcessor processor;

    public RfetmClubConsolidationRunner(RfetmClubConsolidationProcessor processor) {
        this.processor = processor;
    }

    public ClubConsolidationSummary run(Path teamsFolderPath, String season) {
        return run(teamsFolderPath, season, ConsolidationMode.WRITE);
    }

    @Transactional
    public ClubConsolidationSummary run(Path teamsFolderPath, String season, ConsolidationMode mode) {
        return processor.process(teamsFolderPath, season, mode);
    }

}
