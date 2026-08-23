package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record FederatedClubPlayerReadModel(
        UUID playerSeasonId,
        UUID federatedPlayerId,
        String federatedPlayerName,
        String registrationName,
        String license,
        ImportSource source,
        Season season,
        List<String> competitions,
        UUID canonicalPlayerId,
        String canonicalPlayerName) {

    public FederatedClubPlayerReadModel {
        Objects.requireNonNull(playerSeasonId, "playerSeasonId must not be null");
        Objects.requireNonNull(registrationName, "registrationName must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(season, "season must not be null");
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
    }

    public FederatedClubPlayerReadModel(
            UUID playerSeasonId,
            UUID federatedPlayerId,
            String federatedPlayerName,
            String registrationName,
            String license,
            ImportSource source,
            Season season) {
        this(playerSeasonId, federatedPlayerId, federatedPlayerName, registrationName, license, source, season,
                List.of(), null, null);
    }

    public FederatedClubPlayerReadModel(
            UUID playerSeasonId,
            UUID federatedPlayerId,
            String federatedPlayerName,
            String registrationName,
            String license,
            ImportSource source,
            Season season,
            List<String> competitions) {
        this(playerSeasonId, federatedPlayerId, federatedPlayerName, registrationName, license, source, season,
                competitions, null, null);
    }
}
