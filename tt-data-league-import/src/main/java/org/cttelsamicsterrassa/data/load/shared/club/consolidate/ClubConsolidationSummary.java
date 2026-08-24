package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;

public record ClubConsolidationSummary(
        ImportSource source,
        ConsolidationMode mode,
        int scannedRegistrations,
        int exactGroups,
        int acceptedFuzzyGroups,
        int clubsCreated,
        int canonicalLinksCreated,
        int registrationsReassociated,
        int alreadyCorrectRegistrations,
        List<ConsolidatedClub> consolidations,
        List<ConsolidationWarning> warnings,
        List<ConsolidationWarning> errors
) {
}
