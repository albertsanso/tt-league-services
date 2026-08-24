package org.cttelsamicsterrassa.data.core.application.club.find.dto;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FederatedClubCompetitionDetailsReadModel(
        UUID federatedClubId,
        String federatedClubName,
        ImportSource source,
        String competition,
        Season season,
        List<FederatedClubMatchReadModel> matches,
        UUID canonicalClubId,
        String canonicalClubName) {

    public FederatedClubCompetitionDetailsReadModel {
        Objects.requireNonNull(federatedClubId, "federatedClubId must not be null");
        Objects.requireNonNull(federatedClubName, "federatedClubName must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(competition, "competition must not be null");
        Objects.requireNonNull(season, "season must not be null");
        matches = List.copyOf(Objects.requireNonNull(matches, "matches must not be null"));
    }

    public FederatedClubCompetitionDetailsReadModel(
            UUID federatedClubId,
            String federatedClubName,
            ImportSource source,
            String competition,
            Season season,
            List<FederatedClubMatchReadModel> matches) {
        this(federatedClubId, federatedClubName, source, competition, season, matches, null, null);
    }
}
