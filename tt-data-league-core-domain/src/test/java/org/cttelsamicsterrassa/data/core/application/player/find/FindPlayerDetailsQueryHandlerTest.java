package org.cttelsamicsterrassa.data.core.application.player.find;

import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
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

    @Test
    void resolvesAHomeSinglesOpponentByCanonicalIdentity() {
        UUID playerId = UUID.randomUUID();
        Season season = Season.of(2025);
        Player player = Player.createExisting(playerId, "Anna Player");
        FederatedPlayer federatedPlayer = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", player);
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", federatedPlayer, season);
        Player opponent = Player.createExisting(UUID.randomUUID(), "Opponent Player");
        FederatedPlayer opponentFederated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Opponent Player", opponent);
        PlayerSeason opponentSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Opponent Player", "456", opponentFederated, season);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Barcelona", season, null);
        Match match = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(3).awayGamesWon(1)
                .winnerTeam(homeTeam).createExisting();
        Lineup lineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .team(homeTeam).player(registration).createExisting();
        Game game = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .gameNumber(1).type("INDIVIDUAL").homePlayer(registration).awayPlayer(opponentSeason)
                .homeSetsWon(3).awaySetsWon(1).winnerSide("HOME").createExisting();

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        when(playerRepository.findPlayerById(playerId)).thenReturn(Optional.of(player));
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(playerId)).thenReturn(List.of(federatedPlayer));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(federatedPlayer.getId())))
                .thenReturn(List.of(registration));
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(List.of(registration.getId()))).thenReturn(List.of(lineup));
        when(gameRepository.findGamesByMatchIds(List.of(match.getId()))).thenReturn(List.of(game));
        when(doublesPairRepository.findDoublesPairsByGameIds(List.of(game.getId()))).thenReturn(List.of());

        PlayerDetailsReadModel details = new FindPlayerDetailsQueryHandler(
                playerRepository, federatedPlayerRepository, playerSeasonRepository, lineupRepository,
                gameRepository, doublesPairRepository).handle(new FindPlayerDetailsQuery(playerId)).getResponse();

        assertEquals(opponent.getId(), details.matches().getFirst().games().getFirst().opponents().getFirst().playerId());
        assertEquals("win", details.matches().getFirst().games().getFirst().result());
    }

    @Test
    void filtersMatchesBySourceSeasonAndCompetition() {
        UUID playerId = UUID.randomUUID();
        Season rfetmSeason = Season.of(2023);
        Season fcttSeason = Season.of(2024);
        Player player = Player.createExisting(playerId, "Anna Player");
        FederatedPlayer rfetmFederated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna RFETM", player);
        FederatedPlayer fcttFederated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "Anna FCTT", player);
        PlayerSeason rfetmRegistration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", rfetmFederated, rfetmSeason);
        PlayerSeason fcttRegistration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "Anna Player", "456", fcttFederated, fcttSeason);
        Team rfetmHome = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "RFETM Home", rfetmSeason, null);
        Team rfetmAway = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "RFETM Away", rfetmSeason, null);
        Team fcttHome = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "FCTT Home", fcttSeason, null);
        Team fcttAway = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "FCTT Away", fcttSeason, null);
        Match rfetmMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Divisió")
                .season(rfetmSeason).homeTeam(rfetmHome).awayTeam(rfetmAway).winnerTeam(rfetmHome).createExisting();
        Match fcttMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).competition("Preferent")
                .season(fcttSeason).homeTeam(fcttHome).awayTeam(fcttAway).winnerTeam(fcttHome).createExisting();
        Lineup rfetmLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(rfetmMatch)
                .team(rfetmHome).player(rfetmRegistration).createExisting();
        Lineup fcttLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).match(fcttMatch)
                .team(fcttHome).player(fcttRegistration).createExisting();

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        when(playerRepository.findPlayerById(playerId)).thenReturn(Optional.of(player));
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(playerId))
                .thenReturn(List.of(rfetmFederated, fcttFederated));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(
                List.of(fcttFederated.getId(), rfetmFederated.getId())))
                .thenReturn(List.of(rfetmRegistration, fcttRegistration));
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(
                List.of(rfetmRegistration.getId(), fcttRegistration.getId())))
                .thenReturn(List.of(rfetmLineup, fcttLineup));

        PlayerDetailsReadModel details = new FindPlayerDetailsQueryHandler(
                playerRepository, federatedPlayerRepository, playerSeasonRepository, lineupRepository)
                .handle(new FindPlayerDetailsQuery(playerId, ImportSource.FCTT, fcttSeason, "Preferent"))
                .getResponse();

        assertEquals(List.of(fcttMatch.getId()), details.matches().stream().map(value -> value.id()).toList());
        assertEquals(1, details.statistics().size());
        assertEquals(2, details.competitions().size());
    }
}
