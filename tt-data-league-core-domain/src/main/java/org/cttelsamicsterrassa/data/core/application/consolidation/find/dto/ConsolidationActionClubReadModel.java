package org.cttelsamicsterrassa.data.core.application.consolidation.find.dto;

import java.util.UUID;

/**
 * A club as it was named when the consolidation ran. The id may no longer resolve to a club.
 */
public record ConsolidationActionClubReadModel(UUID clubId, String clubName) {
}
