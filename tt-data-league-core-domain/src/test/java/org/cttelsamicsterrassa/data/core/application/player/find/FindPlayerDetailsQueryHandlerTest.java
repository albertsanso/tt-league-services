package org.cttelsamicsterrassa.data.core.application.player.find;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindPlayerDetailsQueryHandlerTest {

    @Test
    void mapsThePlayerLineupTeamForDrawnMatches() {
        UUID playerId = UUID.randomUUID();
        Season season = Season.of(2025);
        Player player = Player.createExisting(playerId, "Anna Player");
        FederatedPlayer federatedPlayer = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", player);
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", federatedPlayer, season);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Barcelona", season, null);
        Match match = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(3).awayGamesWon(3)
                .createExisting();
        Lineup lineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .team(awayTeam).player(registration).createExisting();

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        when(playerRepository.findPlayerById(playerId)).thenReturn(Optional.of(player));
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(playerId)).thenReturn(List.of(federatedPlayer));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(federatedPlayer.getId())))
                .thenReturn(List.of(registration));
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(List.of(registration.getId()))).thenReturn(List.of(lineup));

        PlayerDetailsReadModel details = new FindPlayerDetailsQueryHandler(
                playerRepository, federatedPlayerRepository, playerSeasonRepository, lineupRepository)
                .handle(new FindPlayerDetailsQuery(playerId)).getResponse();

        assertEquals("draw", details.matches().getFirst().result());
        assertEquals("Club Barcelona", details.matches().getFirst().playerTeam());
    }
}
