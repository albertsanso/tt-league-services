package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00080: exercises {@code replaceMatchContent} (SCHEDULED-to-PLAYED upgrade and PLAYED
 * correction) and {@code updateSchedule} against a real (H2) database through the real ports.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchContentReplacementJpaTest {

    private static final Season SEASON = Season.of(2025);
    private static final String COMPETITION = "divisio-honor-femenino";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private SetScoreRepository setScoreRepository;
    @Autowired
    private LineupRepository lineupRepository;
    @Autowired
    private DoublesPairRepository doublesPairRepository;
    @Autowired
    private PlayerSeasonRepository playerSeasonRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private FederatedPlayerRepository federatedPlayerRepository;

    @Test
    void upgradeReplacesAScheduledFixtureWithPlayedContentPreservingTheId() {
        Team home = storedTeam("UP HOME");
        Team away = storedTeam("UP AWAY");
        Match scheduled = scheduledMatch(UUID.randomUUID(), home, away, 1);

        PlayerSeason anna = storedPlayerSeason("20001");
        PlayerSeason marc = storedPlayerSeason("20002");
        Match played = playedReplacement(scheduled, home, away);
        Game single = game(played, 1, "INDIVIDUAL", "HOME", 3, 1);
        Game doubles = game(played, 2, "DOUBLES", "AWAY", 1, 3);
        MatchContent content = new MatchContent(played,
                List.of(lineup(played, home, "A", 1, anna), lineup(played, away, "A", 1, marc)),
                List.of(single, doubles),
                List.of(setScore(single, 1, 11, 5), setScore(single, 2, 9, 11), setScore(doubles, 1, 8, 11)),
                List.of(pair(single, "HOME", anna), pair(single, "AWAY", marc)));

        matchRepository.replaceMatchContent(content);

        Match stored = matchRepository.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals(scheduled.getId(), stored.getId());
        assertEquals(MatchStatus.PLAYED, stored.getStatus());
        assertEquals(5, stored.getHomeGamesWon());
        assertEquals(home.getId(), stored.getWinnerTeam().getId());
        assertEquals(2, lineupRepository.findLineupsByMatchId(stored.getId()).size());
        List<Game> storedGames = gameRepository.findGamesByMatchId(stored.getId());
        assertEquals(2, storedGames.size());
        List<UUID> gameIds = storedGames.stream().map(Game::getId).toList();
        assertEquals(3, setScoreRepository.findSetScoresByGameIds(gameIds).size());
        assertEquals(2, doublesPairRepository.findDoublesPairsByGameIds(gameIds).size());
    }

    @Test
    void correctionReplacesExistingChildrenWithoutDuplicates() {
        Team home = storedTeam("CORR HOME");
        Team away = storedTeam("CORR AWAY");
        Match original = playedMatch(UUID.randomUUID(), home, away, 3);
        Game oldGame = game(original, 1, "INDIVIDUAL", "HOME", 3, 0);
        gameRepository.saveGames(List.of(oldGame));
        setScoreRepository.saveSetScores(List.of(setScore(oldGame, 1, 11, 4)));
        UUID oldGameId = oldGame.getId();

        Match replacement = unsavedPlayed(original.getId(), home, away, 3, 2, 5, away);
        Game newGame = game(replacement, 1, "INDIVIDUAL", "AWAY", 1, 3);
        matchRepository.replaceMatchContent(new MatchContent(replacement, List.of(),
                List.of(newGame), List.of(setScore(newGame, 1, 5, 11)), List.of()));

        Match stored = matchRepository.findMatchById(original.getId()).orElseThrow();
        assertEquals(2, stored.getHomeGamesWon());
        assertEquals(5, stored.getAwayGamesWon());
        assertEquals(away.getId(), stored.getWinnerTeam().getId());
        List<Game> storedGames = gameRepository.findGamesByMatchId(stored.getId());
        assertEquals(1, storedGames.size());
        assertEquals(newGame.getId(), storedGames.getFirst().getId());
        assertTrue(storedGames.stream().noneMatch(value -> value.getId().equals(oldGameId)));
        assertEquals(1, setScoreRepository
                .findSetScoresByGameIds(List.of(storedGames.getFirst().getId())).size());
    }

    @Test
    void rejectsUnknownIdAndChangedNaturalKeyLeavingDataUnchanged() {
        Team home = storedTeam("REJ HOME");
        Team away = storedTeam("REJ AWAY");
        Match stored = playedMatch(UUID.randomUUID(), home, away, 3);
        Game existing = game(stored, 1, "INDIVIDUAL", "HOME", 3, 1);
        gameRepository.saveGames(List.of(existing));

        Match unknown = unsavedPlayed(UUID.randomUUID(), home, away, 9, 5, 0, home);
        assertThrows(IllegalStateException.class, () -> matchRepository.replaceMatchContent(
                new MatchContent(unknown, List.of(), List.of(), List.of(), List.of())));

        Match movedRound = Match.builder().id(stored.getId()).source(ImportSource.RFETM)
                .competition(COMPETITION).season(SEASON).groupNumber(1).round(7)
                .homeTeam(home).awayTeam(away).homeGamesWon(5).awayGamesWon(0).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();
        IllegalStateException keyFailure = assertThrows(IllegalStateException.class,
                () -> matchRepository.replaceMatchContent(
                        new MatchContent(movedRound, List.of(), List.of(), List.of(), List.of())));
        assertTrue(keyFailure.getMessage().contains("natural key"));

        Match unchanged = matchRepository.findMatchById(stored.getId()).orElseThrow();
        assertEquals(5, unchanged.getHomeGamesWon());
        assertEquals(1, gameRepository.findGamesByMatchId(stored.getId()).size());
    }

    @Test
    void updateScheduleRewritesOnlyTheScheduleFieldsOfAScheduledMatch() {
        Team home = storedTeam("SCH HOME");
        Team away = storedTeam("SCH AWAY");
        Match scheduled = scheduledMatch(UUID.randomUUID(), home, away, 1);

        matchRepository.updateSchedule(scheduled.getId(), new MatchSchedule(
                ZonedDateTime.parse("2025-12-01T19:30:00+01:00[Europe/Madrid]"),
                "Terrassa", "Pavellol", "Referee One", "R-1"));

        Match stored = matchRepository.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus());
        assertEquals("Terrassa", stored.getCity());
        assertEquals("Pavellol", stored.getVenue());
        assertEquals("Referee One", stored.getRefereeName());
        assertEquals(ZonedDateTime.parse("2025-12-01T19:30:00+01:00[Europe/Madrid]"), stored.getDateTime());
        assertEquals(home.getId(), stored.getHomeTeam().getId());

        Match played = playedMatch(UUID.randomUUID(), home, away, 2);
        assertThrows(IllegalStateException.class, () -> matchRepository.updateSchedule(
                played.getId(), new MatchSchedule(null, null, null, null, null)));
        assertThrows(IllegalStateException.class, () -> matchRepository.updateSchedule(
                UUID.randomUUID(), new MatchSchedule(null, null, null, null, null)));
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match scheduledMatch(UUID id, Team home, Team away, int round) {
        Match match = Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .status(MatchStatus.SCHEDULED).createNew();
        matchRepository.saveMatch(match);
        return match;
    }

    private Match playedMatch(UUID id, Team home, Team away, int round) {
        Match match = Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .homeGamesWon(5).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createNew();
        matchRepository.saveMatch(match);
        return match;
    }

    private Match unsavedPlayed(UUID id, Team home, Team away, int round,
                                int homeGamesWon, int awayGamesWon, Team winner) {
        return Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .homeGamesWon(homeGamesWon).awayGamesWon(awayGamesWon).winnerTeam(winner)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private Match playedReplacement(Match scheduled, Team home, Team away) {
        return Match.builder().id(scheduled.getId()).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(scheduled.getRound()).homeTeam(home).awayTeam(away)
                .homeGamesWon(5).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private Team storedTeam(String name) {
        FederatedClub club = FederatedClub.createNew(ImportSource.RFETM, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }

    private PlayerSeason storedPlayerSeason(String license) {
        Player canonical =
                Player.createExisting(
                        UUID.randomUUID(), "PLAYER " + license);
        playerRepository.savePlayer(canonical);
        FederatedPlayer federated =
                FederatedPlayer.createExisting(
                        UUID.randomUUID(), ImportSource.RFETM, "PLAYER " + license, canonical);
        federatedPlayerRepository.saveFederatedPlayer(federated);
        PlayerSeason playerSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "PLAYER " + license, license, federated, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        return playerSeason;
    }

    private static Lineup lineup(Match match, Team team, String letter, int position, PlayerSeason player) {
        return Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .team(team).letter(letter).position(position).player(player).createNew();
    }

    private static Game game(Match match, int number, String type, String winnerSide,
                             int homeSetsWon, int awaySetsWon) {
        return Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .gameNumber(number).type(type).crossover("").winnerSide(winnerSide)
                .homeSetsWon(homeSetsWon).awaySetsWon(awaySetsWon).notPlayed(false).createNew();
    }

    private static SetScore setScore(Game game, int setNumber, int homePoints, int awayPoints) {
        return SetScore.builder().id(UUID.randomUUID()).game(game).setNumber(setNumber)
                .homePoints(homePoints).awayPoints(awayPoints).build();
    }

    private static DoublesPair pair(Game game, String side, PlayerSeason player) {
        return DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(game).side(side).player(player).build();
    }
}
