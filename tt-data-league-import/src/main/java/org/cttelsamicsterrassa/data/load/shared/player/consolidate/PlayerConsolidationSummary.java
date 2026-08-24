package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationWarning;

import java.util.List;

public record PlayerConsolidationSummary(
        ImportSource source,
        ConsolidationMode mode,
        int scannedRegistrations,
        int exactGroups,
        int playersCreated,
        int canonicalLinksCreated,
        int registrationsReassociated,
        int alreadyCorrectRegistrations,
        List<ConsolidationWarning> warnings,
        List<ConsolidationWarning> errors
) {
}
