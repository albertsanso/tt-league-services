package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.player.PlayerConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.player.PlayerSeasonConsolidationProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlayerConsolidationRunner {
    private final PlayerSeasonConsolidationProcessor processor;

    public PlayerConsolidationRunner(PlayerSeasonConsolidationProcessor processor) {
        this.processor = processor;
    }

    @Transactional
    public PlayerConsolidationSummary run(ImportSource source, ConsolidationMode mode) {
        return processor.consolidate(source, mode);
    }
}
