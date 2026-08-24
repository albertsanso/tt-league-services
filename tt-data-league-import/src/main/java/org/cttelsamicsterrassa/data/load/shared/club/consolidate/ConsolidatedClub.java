package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.UUID;

public record ConsolidatedClub(
        ImportSource source,
        String normalizedComparisonKey,
        String canonicalDisplayName,
        String matchRule,
        double confidence,
        List<UUID> registrationIds
) {
}
