package org.cttelsamicsterrassa.data.core.application.player.find;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindFederatedPlayersByStringInNameQueryHandlerTest {

    @Test
    void includesDistinctParticipatedSeasonsInSearchResults() {
        FederatedPlayerRepository playerRepository = mock(FederatedPlayerRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayer player = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", null);
        PlayerSeason currentSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "1", player, Season.of(2025));
        PlayerSeason previousSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "1", player, Season.of(2024));
        when(playerRepository.findAllFederatedPlayersBySourceAndFragmentsInName(
                null, List.of("Anna"))).thenReturn(List.of(player));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(player.getId())))
                .thenReturn(List.of(currentSeason, previousSeason, currentSeason));

        List<PlayerSearchReadModel> results = new FindFederatedPlayersByStringInNameQueryHandler(
                playerRepository, playerSeasonRepository)
                .handle(new FindFederatedPlayersByStringInNameQuery("Anna")).getResponse();

        assertEquals(List.of("2025-2026", "2024-2025"), results.getFirst().seasons());
    }
}
