package org.cttelsamicsterrassa.data.core.application.club.find.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public record FederatedClubMatchReadModel(
        UUID id,
        String homeTeam,
        String awayTeam,
        Integer homeGamesWon,
        Integer awayGamesWon,
        String result,
        int round,
        ZonedDateTime dateTime,
        String city,
        String venue) {

    public FederatedClubMatchReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        Objects.requireNonNull(result, "result must not be null");
    }
}
