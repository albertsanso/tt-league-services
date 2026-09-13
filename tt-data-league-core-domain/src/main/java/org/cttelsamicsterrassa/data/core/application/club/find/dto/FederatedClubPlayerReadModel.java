package org.cttelsamicsterrassa.data.core.application.club.find.dto;

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
        String canonicalPlayerName,
        int matchCount,
        int wins,
        int draws,
        int losses,
        List<PlayerCompetitionResultReadModel> competitionResults) {

    public FederatedClubPlayerReadModel {
        Objects.requireNonNull(playerSeasonId, "playerSeasonId must not be null");
        Objects.requireNonNull(registrationName, "registrationName must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(season, "season must not be null");
        competitions = List.copyOf(Objects.requireNonNull(competitions, "competitions must not be null"));
        competitionResults = List.copyOf(
                Objects.requireNonNull(competitionResults, "competitionResults must not be null"));
        if (matchCount < 0 || wins < 0 || draws < 0 || losses < 0) {
            throw new IllegalArgumentException("Player result totals must not be negative");
        }
    }

    public FederatedClubPlayerReadModel(
            UUID playerSeasonId,
            UUID federatedPlayerId,
            String federatedPlayerName,
            String registrationName,
            String license,
            ImportSource source,
            Season season,
            List<String> competitions,
            UUID canonicalPlayerId,
            String canonicalPlayerName,
            int matchCount,
            int wins,
            int draws,
            int losses) {
        this(playerSeasonId, federatedPlayerId, federatedPlayerName, registrationName, license, source, season,
                competitions, canonicalPlayerId, canonicalPlayerName, matchCount, wins, draws, losses, List.of());
    }

    public FederatedClubPlayerReadModel(
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
        this(playerSeasonId, federatedPlayerId, federatedPlayerName, registrationName, license, source, season,
                competitions, canonicalPlayerId, canonicalPlayerName, 0, 0, 0, 0, List.of());
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
