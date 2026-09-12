package org.cttelsamicsterrassa.data.core.application.match.find;

import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindMatchDetailsQueryHandlerTest {

    @Test
    void groupsPastMatchesByExactLineupSetIgnoringBoardLetterAndComputesTeamForm() {
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Club", season, null);

        Player canonicalP1 = Player.createExisting(UUID.randomUUID(), "Player One");
        Player canonicalP2 = Player.createExisting(UUID.randomUUID(), "Player Two");
        Player canonicalP3 = Player.createExisting(UUID.randomUUID(), "Player Three");
        FederatedPlayer fp1 = FederatedPlayer.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", canonicalP1);
        FederatedPlayer fp2 = FederatedPlayer.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player Two", canonicalP2);
        FederatedPlayer fp3 = FederatedPlayer.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player Three", canonicalP3);
        PlayerSeason p1 = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", "1", fp1, season);
        PlayerSeason p2 = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player Two", "2", fp2, season);
        PlayerSeason p3 = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player Three", "3", fp3, season);

        Match match1 = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(2).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();
        Match match2 = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-08T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(1).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();
        Match match3 = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(3).dateTime(ZonedDateTime.parse("2025-10-25T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(4).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();

        // match1: same players as the current lineup, boards swapped (A<->B) — still counts as the same set.
        Lineup match1LineupA = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match1)
                .team(homeTeam).letter("A").player(p2).createExisting();
        Lineup match1LineupB = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match1)
                .team(homeTeam).letter("B").player(p1).createExisting();
        // match2: identical set and letters to the current lineup.
        Lineup match2LineupA = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match2)
                .team(homeTeam).letter("A").player(p1).createExisting();
        Lineup match2LineupB = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match2)
                .team(homeTeam).letter("B").player(p2).createExisting();
        // match3: a different player (p3 instead of p2) — must not count toward the current lineup's group.
        Lineup match3LineupA = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match3)
                .team(homeTeam).letter("A").player(p1).createExisting();
        Lineup match3LineupB = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match3)
                .team(homeTeam).letter("B").player(p3).createExisting();
        Lineup currentLineupA = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(current)
                .team(homeTeam).letter("A").player(p1).createExisting();
        Lineup currentLineupB = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(current)
                .team(homeTeam).letter("B").player(p2).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(List.of(homeTeam.getId()), ImportSource.RFETM))
                .thenReturn(List.of(match1, match2, match3));
        when(lineupRepository.findLineupsByMatchId(current.getId()))
                .thenReturn(List.of(currentLineupA, currentLineupB));
        when(lineupRepository.findAllLineupsByMatchIds(anyList()))
                .thenReturn(List.of(match1LineupA, match1LineupB, match2LineupA, match2LineupB,
                        match3LineupA, match3LineupB));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        MatchDetailReadModel.AlignmentStabilityReadModel homeAlignment = details.homeAlignmentStability();
        assertEquals(3, homeAlignment.timesFielded());
        assertEquals(1, homeAlignment.wins());
        assertEquals(0, homeAlignment.draws());
        assertEquals(2, homeAlignment.losses());
        assertEquals(100.0 / 3.0, homeAlignment.winRate(), 0.001);
        assertEquals(50.0, homeAlignment.teamOverallWinRate());

        // The away team has no lineup data in this fixture, so its lineup set is empty and the
        // match is reported as a first-time combination ("New combination") with no win/loss data.
        MatchDetailReadModel.AlignmentStabilityReadModel awayAlignment = details.awayAlignmentStability();
        assertEquals(1, awayAlignment.timesFielded());
        assertEquals(0, awayAlignment.wins());
        assertEquals(0, awayAlignment.losses());
        assertNull(awayAlignment.winRate());
        assertEquals(0.0, awayAlignment.teamOverallWinRate());

        MatchDetailReadModel.TeamFormReadModel homeForm = details.homeTeamForm();
        assertEquals(3, homeForm.lastResults().size());
        assertEquals(match3.getId(), homeForm.lastResults().getFirst().matchId());
        assertEquals("win", homeForm.lastResults().getFirst().result());
        assertEquals(match2.getId(), homeForm.lastResults().getLast().matchId());
        assertEquals("loss", homeForm.lastResults().getLast().result());
        assertEquals(100.0 / 3.0, homeForm.lastWinRate(), 0.001);
        assertNull(homeForm.previousWinRate());
        assertEquals(50.0, homeForm.overallWinRate());
    }

    @Test
    void exposesTheHomeTeamsClubIdAndLeavesItNullWhenTheTeamHasNoFederatedClub() {
        Season season = Season.of(2025);
        FederatedClub homeClub = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club");
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club", season, homeClub);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Club", season, null);
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(List.of(homeTeam.getId()), ImportSource.RFETM))
                .thenReturn(List.of(current));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(homeClub.getId(), details.homeTeam().clubId());
        assertNull(details.awayTeam().clubId());
    }

    @Test
    void excludesTheViewedMatchFromItsOwnHistory() {
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Club", season, null);
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        // The repository would naturally include the current match among "all matches for this team".
        when(matchRepository.findAllMatchesByTeamIdsAndSource(List.of(homeTeam.getId()), ImportSource.RFETM))
                .thenReturn(List.of(current));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(0, details.homeTeamForm().lastResults().size());
        assertEquals(100.0, details.homeTeamForm().overallWinRate());
        assertEquals(1, details.homeAlignmentStability().timesFielded());
    }
}
