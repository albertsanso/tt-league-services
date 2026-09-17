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
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "Preferent"))
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
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "Preferent"))
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
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "Preferent"))
                .thenReturn(List.of(current));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(0, details.homeTeamForm().lastResults().size());
        assertEquals(100.0, details.homeTeamForm().overallWinRate());
        assertEquals(1, details.homeAlignmentStability().timesFielded());
    }

    @Test
    void scopesTeamFormToTheViewedMatchsOwnCompetition() {
        // A Team is keyed by source+name+season only, so a club fielding teams in several
        // competitions shares a single registration. A lookup scoped to source+season alone lets a
        // more recent match from another competition crowd this competition's form strip.
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Away Club", season, null);

        Match veteransWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match otherCompetitionLoss = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .competition("1a Divisio")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(1).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
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
        // What a source+season-only lookup would hand back: the other competition's later match too.
        when(matchRepository.findAllMatchesByTeamIdsAndSource(List.of(homeTeam.getId()), ImportSource.BCNESA))
                .thenReturn(List.of(veteransWin, otherCompetitionLoss));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.BCNESA, season, "Vet 1a"))
                .thenReturn(List.of(veteransWin));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        MatchDetailReadModel.TeamFormReadModel homeForm = details.homeTeamForm();
        assertEquals(1, homeForm.lastResults().size());
        assertEquals(veteransWin.getId(), homeForm.lastResults().getFirst().matchId());
        assertEquals("win", homeForm.lastResults().getFirst().result());
        assertEquals(100.0, homeForm.lastWinRate());
    }

    @Test
    void reportsFormGoingIntoTheViewedMatchRatherThanTheSeasonsLatestResults() {
        // Viewing a match from November must not report results from the following March: the
        // recent-form strip is the team's form going into that match.
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Away Club", season, null);

        Match earlierWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();
        Match laterLoss = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(3).dateTime(ZonedDateTime.parse("2026-03-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(1).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.BCNESA, season, "Vet 1a"))
                .thenReturn(List.of(earlierWin, laterLoss));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        MatchDetailReadModel.TeamFormReadModel homeForm = details.homeTeamForm();
        assertEquals(1, homeForm.lastResults().size());
        assertEquals(earlierWin.getId(), homeForm.lastResults().getFirst().matchId());
        assertEquals(100.0, homeForm.lastWinRate());
        // The later match still counts toward the team's season-wide win rate.
        assertEquals(200.0 / 3.0, homeForm.overallWinRate(), 0.001);
    }

    @Test
    void excludesATieOutsideTheTieEligibleCompetitionsFromTeamFormAndAlignmentEntirely() {
        // FEAT-00066: a tied past match in a non-tie-eligible competition must not appear at all
        // in team form or alignment stability - it does not consume a recent-form slot, nor count
        // toward timesFielded, wins, draws or losses.
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Club", season, null);
        Player canonical = Player.createExisting(UUID.randomUUID(), "Player One");
        FederatedPlayer federated = FederatedPlayer.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", canonical);
        PlayerSeason player = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", "1", federated, season);

        Match ineligibleTie = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("preferent")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(3).awayGamesWon(3)
                .createExisting();
        Match pastWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("preferent")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-08T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("preferent")
                .season(season).round(3).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();

        Lineup ineligibleTieLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(ineligibleTie).team(homeTeam).letter("A").player(player).createExisting();
        Lineup pastWinLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(pastWin).team(homeTeam).letter("A").player(player).createExisting();
        Lineup currentLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(current).team(homeTeam).letter("A").player(player).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "preferent"))
                .thenReturn(List.of(ineligibleTie, pastWin));
        when(lineupRepository.findLineupsByMatchId(current.getId())).thenReturn(List.of(currentLineup));
        when(lineupRepository.findAllLineupsByMatchIds(anyList()))
                .thenReturn(List.of(ineligibleTieLineup, pastWinLineup));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(1, details.homeTeamForm().lastResults().size());
        assertEquals(pastWin.getId(), details.homeTeamForm().lastResults().getFirst().matchId());
        assertEquals(100.0, details.homeTeamForm().overallWinRate());

        MatchDetailReadModel.AlignmentStabilityReadModel homeAlignment = details.homeAlignmentStability();
        assertEquals(2, homeAlignment.timesFielded());
        assertEquals(2, homeAlignment.wins());
        assertEquals(0, homeAlignment.draws());
        assertEquals(0, homeAlignment.losses());
    }

    @Test
    void countsATieAsADrawInATieEligibleCompetition() {
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away Club", season, null);
        Player canonical = Player.createExisting(UUID.randomUUID(), "Player One");
        FederatedPlayer federated = FederatedPlayer.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", canonical);
        PlayerSeason player = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Player One", "1", federated, season);

        Match eligibleTie = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .competition("super-divisio-masculino")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(3).awayGamesWon(3)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .competition("super-divisio-masculino")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-08T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();

        Lineup eligibleTieLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(eligibleTie).team(homeTeam).letter("A").player(player).createExisting();
        Lineup currentLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(current).team(homeTeam).letter("A").player(player).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "super-divisio-masculino"))
                .thenReturn(List.of(eligibleTie));
        when(lineupRepository.findLineupsByMatchId(current.getId())).thenReturn(List.of(currentLineup));
        when(lineupRepository.findAllLineupsByMatchIds(anyList())).thenReturn(List.of(eligibleTieLineup));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(1, details.homeTeamForm().lastResults().size());
        assertEquals("draw", details.homeTeamForm().lastResults().getFirst().result());

        MatchDetailReadModel.AlignmentStabilityReadModel homeAlignment = details.homeAlignmentStability();
        assertEquals(2, homeAlignment.timesFielded());
        assertEquals(1, homeAlignment.wins());
        assertEquals(1, homeAlignment.draws());
        assertEquals(0, homeAlignment.losses());
    }

    @Test
    void scopesPlayerFormToTheViewedMatchsOwnCompetition() {
        // FEAT-00072: a player registered once per source+season plays in several competitions, so
        // an unscoped lookup lets another competition's match crowd this strip out.
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Away Club", season, null);
        Player canonical = Player.createExisting(UUID.randomUUID(), "Player One");
        FederatedPlayer federated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", canonical);
        PlayerSeason player = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", "1", federated, season);

        Match veteransWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match otherCompetitionLoss = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .competition("1a Divisio")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(1).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();

        Lineup veteransLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(veteransWin).team(homeTeam).letter("A").player(player).createExisting();
        Lineup otherCompetitionLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(otherCompetitionLoss).team(homeTeam).letter("A").player(player).createExisting();
        Lineup currentLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(current).team(homeTeam).letter("A").player(player).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.BCNESA, season, "Vet 1a"))
                .thenReturn(List.of(veteransWin));
        when(lineupRepository.findLineupsByMatchId(current.getId())).thenReturn(List.of(currentLineup));
        when(lineupRepository.findAllLineupsByMatchIds(anyList())).thenReturn(List.of(veteransLineup));
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(canonical.getId()))
                .thenReturn(List.of(federated));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(federated.getId())))
                .thenReturn(List.of(player));
        // The registration's whole season, both competitions - the handler must narrow it itself.
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(List.of(player.getId())))
                .thenReturn(List.of(veteransLineup, otherCompetitionLineup, currentLineup));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        assertEquals(1, details.playerForm().size());
        MatchDetailReadModel.PlayerFormReadModel form = details.playerForm().getFirst();
        assertEquals(1, form.lastResults().size());
        assertEquals(veteransWin.getId(), form.lastResults().getFirst().matchId());
        assertEquals("win", form.lastResults().getFirst().result());
        assertEquals(100.0, form.winRate());
    }

    @Test
    void reportsPlayerFormGoingIntoTheViewedMatch() {
        // FEAT-00072: viewing a November match must not report the player's March results.
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Home Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Away Club", season, null);
        Player canonical = Player.createExisting(UUID.randomUUID(), "Player One");
        FederatedPlayer federated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", canonical);
        PlayerSeason player = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", "1", federated, season);

        Match earlierWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(homeTeam)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(homeTeam)
                .createExisting();
        Match laterLoss = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(3).dateTime(ZonedDateTime.parse("2026-03-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).homeGamesWon(1).awayGamesWon(5).winnerTeam(awayTeam)
                .createExisting();

        Lineup earlierLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(earlierWin).team(homeTeam).letter("A").player(player).createExisting();
        Lineup laterLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(laterLoss).team(homeTeam).letter("A").player(player).createExisting();
        Lineup currentLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(current).team(homeTeam).letter("A").player(player).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.BCNESA, season, "Vet 1a"))
                .thenReturn(List.of(earlierWin, laterLoss));
        when(lineupRepository.findLineupsByMatchId(current.getId())).thenReturn(List.of(currentLineup));
        when(lineupRepository.findAllLineupsByMatchIds(anyList()))
                .thenReturn(List.of(earlierLineup, laterLineup));
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(canonical.getId()))
                .thenReturn(List.of(federated));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(federated.getId())))
                .thenReturn(List.of(player));
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(List.of(player.getId())))
                .thenReturn(List.of(earlierLineup, laterLineup, currentLineup));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        MatchDetailReadModel.PlayerFormReadModel form = details.playerForm().getFirst();
        assertEquals(1, form.lastResults().size());
        assertEquals(earlierWin.getId(), form.lastResults().getFirst().matchId());
        assertEquals(100.0, form.winRate());
    }

    @Test
    void keepsPlayerFormAcrossTeamsWithinTheSameCompetition() {
        // FEAT-00072 must not narrow the window to the lineup's own team: a mid-season transfer's
        // matches for the previous club still count, which is what resolvePlayerSeasonIds is for.
        Season season = Season.of(2025);
        Team previousClub = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Previous Club", season, null);
        Team currentClub = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Current Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.BCNESA, "Away Club", season, null);
        Player canonical = Player.createExisting(UUID.randomUUID(), "Player One");
        FederatedPlayer federated = FederatedPlayer.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", canonical);
        PlayerSeason previousRegistration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", "1", federated, season);
        PlayerSeason currentRegistration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.BCNESA, "Player One", "2", federated, season);

        Match previousClubWin = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .competition("Vet 1a")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(previousClub).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(2).winnerTeam(previousClub)
                .createExisting();
        Match current = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA).competition("Vet 1a")
                .season(season).round(2).dateTime(ZonedDateTime.parse("2025-11-15T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(currentClub).awayTeam(awayTeam).homeGamesWon(5).awayGamesWon(3).winnerTeam(currentClub)
                .createExisting();

        Lineup previousClubLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(previousClubWin).team(previousClub).letter("A").player(previousRegistration).createExisting();
        Lineup currentLineup = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .match(current).team(currentClub).letter("A").player(currentRegistration).createExisting();

        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);
        SetScoreRepository setScoreRepository = mock(SetScoreRepository.class);
        DoublesPairRepository doublesPairRepository = mock(DoublesPairRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        FederatedPlayerRepository federatedPlayerRepository = mock(FederatedPlayerRepository.class);

        when(matchRepository.findMatchById(current.getId())).thenReturn(Optional.of(current));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(currentClub.getId()), ImportSource.BCNESA, season, "Vet 1a"))
                .thenReturn(List.of());
        when(lineupRepository.findLineupsByMatchId(current.getId())).thenReturn(List.of(currentLineup));
        when(lineupRepository.findAllLineupsByMatchIds(anyList())).thenReturn(List.of());
        when(federatedPlayerRepository.findAllFederatedPlayersByPlayerId(canonical.getId()))
                .thenReturn(List.of(federated));
        when(playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(List.of(federated.getId())))
                .thenReturn(List.of(previousRegistration, currentRegistration));
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(
                List.of(previousRegistration.getId(), currentRegistration.getId())))
                .thenReturn(List.of(previousClubLineup, currentLineup));

        MatchDetailReadModel details = new FindMatchDetailsQueryHandler(matchRepository, lineupRepository,
                gameRepository, setScoreRepository, doublesPairRepository, playerSeasonRepository,
                federatedPlayerRepository).handle(new FindMatchDetailsQuery(current.getId())).getResponse();

        MatchDetailReadModel.PlayerFormReadModel form = details.playerForm().getFirst();
        assertEquals(1, form.lastResults().size());
        assertEquals(previousClubWin.getId(), form.lastResults().getFirst().matchId());
        assertEquals("win", form.lastResults().getFirst().result());
    }
}
