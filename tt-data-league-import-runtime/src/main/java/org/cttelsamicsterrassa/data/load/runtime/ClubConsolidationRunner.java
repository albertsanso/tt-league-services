package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubSeasonConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClubConsolidationRunner {

    private final ClubSeasonConsolidationProcessor processor;

    public ClubConsolidationRunner(ClubSeasonConsolidationProcessor processor) {
        this.processor = processor;
    }

    @Transactional
    public ClubConsolidationSummary run(ImportSource source, ConsolidationMode mode) {
        return processor.consolidate(source, mode);
    }
}
