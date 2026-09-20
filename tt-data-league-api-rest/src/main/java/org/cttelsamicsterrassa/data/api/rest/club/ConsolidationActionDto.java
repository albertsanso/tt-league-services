package org.cttelsamicsterrassa.data.api.rest.club;

import org.cttelsamicsterrassa.data.core.application.consolidation.find.dto.ConsolidationActionClubReadModel;
import org.cttelsamicsterrassa.data.core.application.consolidation.find.dto.ConsolidationActionReadModel;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One historic consolidation.
 *
 * <p>The club ids inside {@code sourceClubs} generally no longer resolve: those clubs were deleted
 * by the consolidation itself, and the names are snapshots taken before that happened.</p>
 */
public record ConsolidationActionDto(
        UUID id,
        String type,
        ZonedDateTime occurredOn,
        UUID performedByUserId,
        String performedByUsername,
        String canonicalName,
        List<ConsolidationActionClubDto> sourceClubs,
        List<ConsolidationActionClubDto> targetClubs) {

    public record ConsolidationActionClubDto(UUID clubId, String clubName) {
        static ConsolidationActionClubDto fromObject(ConsolidationActionClubReadModel readModel) {
            return new ConsolidationActionClubDto(readModel.clubId(), readModel.clubName());
        }
    }

    public static ConsolidationActionDto fromObject(ConsolidationActionReadModel readModel) {
        if (readModel == null) {
            return null;
        }
        return new ConsolidationActionDto(
                readModel.id(),
                readModel.type() == null ? null : readModel.type().name(),
                readModel.occurredOn(),
                readModel.performedByUserId(),
                readModel.performedByUsername(),
                readModel.canonicalName(),
                readModel.sourceClubs().stream().map(ConsolidationActionClubDto::fromObject).toList(),
                readModel.targetClubs().stream().map(ConsolidationActionClubDto::fromObject).toList());
    }
}
