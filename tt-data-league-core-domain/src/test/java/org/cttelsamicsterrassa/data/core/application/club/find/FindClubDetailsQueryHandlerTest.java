package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindClubDetailsQueryHandlerTest {

    @Test
    void computesPerPlayerWinLossTotalsFromIndividualGamesRatherThanTeamResult() {
        UUID clubId = UUID.randomUUID();
        Club club = Club.createExisting(clubId, "Club Terrassa");
        Season season = Season.of(2025);
        FederatedClub federatedClub = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", club);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, federatedClub);
        Team rivalA = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival A", season, null);
        Team rivalB = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival B", season, null);

        PlayerSeason anna = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", null, season);
        PlayerSeason marc = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Marc Player", "456", null, season);
        PlayerSeason rivalPlayer = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Rival Player", "789", null, season);

        // Anna's team wins this match overall, but Anna personally loses her own game.
        Match teamWinMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalA).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        // Anna's team loses this match overall, but Anna personally wins her own game.
        Match teamLossMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalB).homeGamesWon(1).awayGamesWon(5)
                .winnerTeam(rivalB).createExisting();

        Game annaLosesInTeamWin = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(teamWinMatch).gameNumber(1).type("INDIVIDUAL")
                .homePlayer(anna).awayPlayer(rivalPlayer).winnerSide("AWAY").createExisting();
        Game annaWinsInTeamLoss = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(teamLossMatch).gameNumber(1).type("INDIVIDUAL")
                .homePlayer(anna).awayPlayer(rivalPlayer).winnerSide("HOME").createExisting();

        ClubRepository clubRepository = mock(ClubRepository.class);
        FederatedClubRepository federatedClubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);

        when(clubRepository.findClubById(clubId)).thenReturn(Optional.of(club));
        when(federatedClubRepository.findAllFederatedClubsByClubId(clubId)).thenReturn(List.of(federatedClub));
        when(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId())).thenReturn(List.of(homeTeam));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(anna, marc));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(teamWinMatch, teamLossMatch));
        when(playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(Map.of());
        when(gameRepository.findGamesByMatchIds(anyList()))
                .thenReturn(List.of(annaLosesInTeamWin, annaWinsInTeamLoss));
        when(doublesPairRepository.findDoublesPairsByGameIds(anyList())).thenReturn(List.of());

        ClubDetailsReadModel details = new FindClubDetailsQueryHandler(
                clubRepository, federatedClubRepository, teamRepository, matchRepository,
                playerSeasonRepository, gameRepository, doublesPairRepository)
                .handle(new FindClubDetailsQuery(clubId)).getResponse();

        FederatedClubPlayerReadModel annaModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(anna.getId())).findFirst().orElseThrow();
        assertEquals(2, annaModel.matchCount());
        assertEquals(1, annaModel.wins());
        assertEquals(0, annaModel.draws());
        assertEquals(1, annaModel.losses());

        FederatedClubPlayerReadModel marcModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(marc.getId())).findFirst().orElseThrow();
        assertEquals(0, marcModel.matchCount());
        assertEquals(0, marcModel.wins());
        assertEquals(0, marcModel.draws());
        assertEquals(0, marcModel.losses());
    }

    @Test
    void computesPerPlayerWinLossTotalsPerCompetitionFromIndividualGames() {
        UUID clubId = UUID.randomUUID();
        Club club = Club.createExisting(clubId, "Club Terrassa");
        Season season = Season.of(2025);
        FederatedClub federatedClub = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", club);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, federatedClub);
        Team rivalA = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival A", season, null);
        Team rivalB = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival B", season, null);

        PlayerSeason anna = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", null, season);
        PlayerSeason rivalPlayer = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Rival Player", "789", null, season);

        Match leagueMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalA).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        Match cupMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Copa")
                .season(season).homeTeam(homeTeam).awayTeam(rivalB).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();

        Game annaWinsInLeague = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(leagueMatch).gameNumber(1).type("INDIVIDUAL")
                .homePlayer(anna).awayPlayer(rivalPlayer).winnerSide("HOME").createExisting();
        Game annaLosesInCup = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(cupMatch).gameNumber(1).type("INDIVIDUAL")
                .homePlayer(anna).awayPlayer(rivalPlayer).winnerSide("AWAY").createExisting();

        ClubRepository clubRepository = mock(ClubRepository.class);
        FederatedClubRepository federatedClubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);

        when(clubRepository.findClubById(clubId)).thenReturn(Optional.of(club));
        when(federatedClubRepository.findAllFederatedClubsByClubId(clubId)).thenReturn(List.of(federatedClub));
        when(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId())).thenReturn(List.of(homeTeam));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(anna));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(leagueMatch, cupMatch));
        when(playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(Map.of(anna.getId(), List.of("Preferent", "Copa")));
        when(gameRepository.findGamesByMatchIds(anyList()))
                .thenReturn(List.of(annaWinsInLeague, annaLosesInCup));
        when(doublesPairRepository.findDoublesPairsByGameIds(anyList())).thenReturn(List.of());

        ClubDetailsReadModel details = new FindClubDetailsQueryHandler(
                clubRepository, federatedClubRepository, teamRepository, matchRepository,
                playerSeasonRepository, gameRepository, doublesPairRepository)
                .handle(new FindClubDetailsQuery(clubId)).getResponse();

        FederatedClubPlayerReadModel annaModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(anna.getId())).findFirst().orElseThrow();

        assertEquals(2, annaModel.matchCount());
        assertEquals(1, annaModel.wins());
        assertEquals(1, annaModel.losses());
        assertEquals(2, annaModel.competitionResults().size());

        var leagueResult = annaModel.competitionResults().stream()
                .filter(result -> result.competition().equals("Preferent")).findFirst().orElseThrow();
        assertEquals(1, leagueResult.matchCount());
        assertEquals(1, leagueResult.wins());
        assertEquals(0, leagueResult.losses());

        var cupResult = annaModel.competitionResults().stream()
                .filter(result -> result.competition().equals("Copa")).findFirst().orElseThrow();
        assertEquals(1, cupResult.matchCount());
        assertEquals(0, cupResult.wins());
        assertEquals(1, cupResult.losses());
    }

    @Test
    void creditsBothMembersOfADoublesPairWithTheirPairsResult() {
        UUID clubId = UUID.randomUUID();
        Club club = Club.createExisting(clubId, "Club Terrassa");
        Season season = Season.of(2025);
        FederatedClub federatedClub = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", club);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, federatedClub);
        Team rivalA = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival A", season, null);

        PlayerSeason anna = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", null, season);
        PlayerSeason marc = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Marc Player", "456", null, season);
        PlayerSeason rivalOne = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Rival One", "111", null, season);
        PlayerSeason rivalTwo = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Rival Two", "222", null, season);

        // The team loses the match overall, but Anna and Marc's doubles pair wins their game.
        Match match = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalA).homeGamesWon(2).awayGamesWon(5)
                .winnerTeam(rivalA).createExisting();

        Game doublesGame = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(match).gameNumber(3).type("DOUBLES").winnerSide("HOME").createExisting();

        DoublesPair annaPair = DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(doublesGame).side("HOME").player(anna).build();
        DoublesPair marcPair = DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(doublesGame).side("HOME").player(marc).build();
        DoublesPair rivalOnePair = DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(doublesGame).side("AWAY").player(rivalOne).build();
        DoublesPair rivalTwoPair = DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(doublesGame).side("AWAY").player(rivalTwo).build();

        ClubRepository clubRepository = mock(ClubRepository.class);
        FederatedClubRepository federatedClubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);

        when(clubRepository.findClubById(clubId)).thenReturn(Optional.of(club));
        when(federatedClubRepository.findAllFederatedClubsByClubId(clubId)).thenReturn(List.of(federatedClub));
        when(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId())).thenReturn(List.of(homeTeam));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(anna, marc));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(match));
        when(playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(Map.of());
        when(gameRepository.findGamesByMatchIds(anyList())).thenReturn(List.of(doublesGame));
        when(doublesPairRepository.findDoublesPairsByGameIds(anyList()))
                .thenReturn(List.of(annaPair, marcPair, rivalOnePair, rivalTwoPair));

        ClubDetailsReadModel details = new FindClubDetailsQueryHandler(
                clubRepository, federatedClubRepository, teamRepository, matchRepository,
                playerSeasonRepository, gameRepository, doublesPairRepository)
                .handle(new FindClubDetailsQuery(clubId)).getResponse();

        FederatedClubPlayerReadModel annaModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(anna.getId())).findFirst().orElseThrow();
        assertEquals(1, annaModel.matchCount());
        assertEquals(1, annaModel.wins());
        assertEquals(0, annaModel.losses());

        FederatedClubPlayerReadModel marcModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(marc.getId())).findFirst().orElseThrow();
        assertEquals(1, marcModel.matchCount());
        assertEquals(1, marcModel.wins());
        assertEquals(0, marcModel.losses());
    }
}
