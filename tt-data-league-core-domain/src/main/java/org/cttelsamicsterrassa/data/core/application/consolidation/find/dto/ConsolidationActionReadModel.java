package org.cttelsamicsterrassa.data.core.application.consolidation.find.dto;

import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ConsolidationActionReadModel(
        UUID id,
        ConsolidationActionType type,
        ZonedDateTime occurredOn,
        UUID performedByUserId,
        String performedByUsername,
        String canonicalName,
        List<ConsolidationActionClubReadModel> sourceClubs,
        List<ConsolidationActionClubReadModel> targetClubs) {
}
