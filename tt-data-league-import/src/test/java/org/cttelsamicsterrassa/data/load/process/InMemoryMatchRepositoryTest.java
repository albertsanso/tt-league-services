package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00080: the in-memory MatchRepository must mirror the JPA adapter's replacement and
 * reschedule contract so processor tests (FEAT-00081) exercise the same semantics.
 */
class InMemoryMatchRepositoryTest {

    private static final Season SEASON = Season.of(2025);
    private static final String COMPETITION = "preferent";

    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private Team home;
    private Team away;
    private PlayerSeason anna;
    private PlayerSeason marc;

    @BeforeEach
    void setUp() {
        lineups = new InMemoryRepositories.Lineups();
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);
        home = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Home", SEASON, null);
        away = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Away", SEASON, null);
        anna = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Anna", "1", null, SEASON);
        marc = PlayerSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Marc", "2", null, SEASON);
    }

    @Test
    void upgradeReplacesScheduledFixtureWithPlayedContentPreservingIdAndOrdering() {
        Match scheduled = savedScheduled(UUID.randomUUID(), 1);
        Match played = playedHeader(scheduled.getId(), 5, 2, home, 1);
        Game single = game(played, 1, "HOME");
        Game doubles = game(played, 2, "AWAY");

        matches.replaceMatchContent(new MatchContent(played,
                List.of(lineup(played, home, anna), lineup(played, away, marc)),
                List.of(single, doubles),
                List.of(setScore(single, 1), setScore(doubles, 1)),
                List.of(pair(single, "HOME", anna))));

        Match stored = matches.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals(scheduled.getId(), stored.getId());
        assertEquals(MatchStatus.PLAYED, stored.getStatus());
        assertEquals(5, stored.getHomeGamesWon());
        assertEquals(2, lineups.saved.size());
        assertEquals(2, games.saved.size());
        assertEquals(2, setScores.saved.size());
        assertEquals(1, doublesPairs.saved.size());
    }

    @Test
    void correctionReplacesExistingChildren() {
        Match original = savedPlayed(UUID.randomUUID(), 3);
        Game oldGame = game(original, 1, "HOME");
        games.saveGames(List.of(oldGame));
        setScores.saveSetScores(List.of(setScore(oldGame, 1)));
        doublesPairs.saveDoublesPairs(List.of(pair(oldGame, "HOME", anna)));
        lineups.saveLineups(List.of(lineup(original, home, anna)));

        Match replacement = playedHeader(original.getId(), 2, 5, away, 3);
        Game newGame = game(replacement, 1, "AWAY");
        matches.replaceMatchContent(new MatchContent(replacement, List.of(),
                List.of(newGame), List.of(setScore(newGame, 1)), List.of()));

        Match stored = matches.findMatchById(original.getId()).orElseThrow();
        assertEquals(2, stored.getHomeGamesWon());
        assertEquals(away.getId(), stored.getWinnerTeam().getId());
        assertEquals(List.of(newGame.getId()),
                games.saved.stream().map(Game::getId).toList());
        assertEquals(1, setScores.saved.size());
        assertTrue(doublesPairs.saved.isEmpty());
        assertTrue(lineups.saved.isEmpty());
    }

    @Test
    void updateScheduleRewritesOnlyTheScheduleFieldsOfAScheduledMatch() {
        Match scheduled = savedScheduled(UUID.randomUUID(), 1);
        ZonedDateTime newDateTime = ZonedDateTime.parse("2025-12-01T19:30:00+01:00[Europe/Madrid]");

        matches.updateSchedule(scheduled.getId(),
                new MatchSchedule(newDateTime, "Terrassa", "Pavellol", "Referee One", "R-1"));

        Match stored = matches.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus());
        assertEquals(newDateTime, stored.getDateTime());
        assertEquals("Terrassa", stored.getCity());
        assertEquals("Pavellol", stored.getVenue());
        assertEquals("Referee One", stored.getRefereeName());
        assertEquals("R-1", stored.getRefereeLicense());
        assertEquals(home.getId(), stored.getHomeTeam().getId());
    }

    @Test
    void rejectionsLeaveMatchesAndChildrenUnchanged() {
        Match original = savedPlayed(UUID.randomUUID(), 3);
        Game oldGame = game(original, 1, "HOME");
        games.saveGames(List.of(oldGame));
        lineups.saveLineups(List.of(lineup(original, home, anna)));

        assertThrows(IllegalStateException.class, () -> matches.replaceMatchContent(
                new MatchContent(playedHeader(UUID.randomUUID(), 5, 0, home, 4),
                        List.of(), List.of(), List.of(), List.of())));

        Match movedRound = playedHeader(original.getId(), 5, 0, home, 8);
        IllegalStateException keyFailure = assertThrows(IllegalStateException.class,
                () -> matches.replaceMatchContent(
                        new MatchContent(movedRound, List.of(), List.of(), List.of(), List.of())));
        assertTrue(keyFailure.getMessage().contains("natural key"));

        assertThrows(IllegalStateException.class,
                () -> matches.updateSchedule(original.getId(),
                        new MatchSchedule(null, null, null, null, null)));
        assertThrows(IllegalStateException.class,
                () -> matches.updateSchedule(UUID.randomUUID(),
                        new MatchSchedule(null, null, null, null, null)));

        Match stored = matches.findMatchById(original.getId()).orElseThrow();
        assertEquals(3, stored.getHomeGamesWon());
        assertEquals(MatchStatus.PLAYED, stored.getStatus());
        assertNull(stored.getCity());
        assertEquals(List.of(oldGame.getId()), games.saved.stream().map(Game::getId).toList());
        assertEquals(1, lineups.saved.size());
    }

    @Test
    void replaceMatchContentOnUnwiredStoresFailsLoudly() {
        InMemoryRepositories.Matches unwired = new InMemoryRepositories.Matches();
        Match played = savedPlayed(UUID.randomUUID(), 1);

        assertThrows(IllegalStateException.class, () -> unwired.replaceMatchContent(
                new MatchContent(playedHeader(played.getId(), 5, 2, home, 1),
                        List.of(), List.of(), List.of(), List.of())));
    }

    @Test
    void findBySourceFixtureIdIsSourceScopedAndRejectsNullArguments() {
        Match withId = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .sourceFixtureId("F-1").competition(COMPETITION).season(SEASON).groupNumber(1).round(1)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(withId);
        Match otherSource = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .sourceFixtureId("F-2").competition(COMPETITION).season(SEASON).groupNumber(1).round(2)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(otherSource);

        assertEquals(withId.getId(), matches.findBySourceFixtureId(ImportSource.RFETM, "F-1").orElseThrow().getId());
        assertTrue(matches.findBySourceFixtureId(ImportSource.RFETM, "F-2").isEmpty());
        assertTrue(matches.findBySourceFixtureId(ImportSource.BCNESA, "F-1").isEmpty());
        assertThrows(NullPointerException.class, () -> matches.findBySourceFixtureId(null, "F-1"));
        assertThrows(NullPointerException.class, () -> matches.findBySourceFixtureId(ImportSource.RFETM, null));
    }

    @Test
    void saveMatchMirrorsTheSourceScopedUniqueFixtureIdConstraint() {
        Match first = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .sourceFixtureId("DUP").competition(COMPETITION).season(SEASON).groupNumber(1).round(1)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(first);

        Match duplicate = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .sourceFixtureId("DUP").competition(COMPETITION).season(SEASON).groupNumber(1).round(2)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        assertThrows(IllegalStateException.class, () -> matches.saveMatch(duplicate));

        Match otherSource = Match.builder().id(UUID.randomUUID()).source(ImportSource.BCNESA)
                .sourceFixtureId("DUP").competition(COMPETITION).season(SEASON).groupNumber(1).round(3)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(otherSource);

        savedScheduled(UUID.randomUUID(), 4);
        savedScheduled(UUID.randomUUID(), 5);
    }

    @Test
    void updateScheduleCarriesTheStoredSourceFixtureId() {
        Match scheduled = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .sourceFixtureId("KEEP-1").competition(COMPETITION).season(SEASON).groupNumber(1).round(1)
                .homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(scheduled);

        matches.updateSchedule(scheduled.getId(),
                new MatchSchedule(null, "Terrassa", null, null, null));

        assertEquals("KEEP-1", matches.findMatchById(scheduled.getId()).orElseThrow().getSourceFixtureId());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match savedScheduled(UUID id, int round) {
        Match match = Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .status(MatchStatus.SCHEDULED).createExisting();
        matches.saveMatch(match);
        return match;
    }

    private Match savedPlayed(UUID id, int round) {
        Match match = Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .homeGamesWon(3).awayGamesWon(1).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();
        matches.saveMatch(match);
        return match;
    }

    private Match playedHeader(UUID id, int homeGamesWon, int awayGamesWon, Team winner, int round) {
        return Match.builder().id(id).source(ImportSource.RFETM).competition(COMPETITION)
                .season(SEASON).groupNumber(1).round(round).homeTeam(home).awayTeam(away)
                .homeGamesWon(homeGamesWon).awayGamesWon(awayGamesWon).winnerTeam(winner)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private static Lineup lineup(Match match, Team team, PlayerSeason player) {
        return Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .team(team).letter("A").position(1).player(player).createExisting();
    }

    private static Game game(Match match, int number, String winnerSide) {
        return Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .gameNumber(number).type("INDIVIDUAL").crossover("").winnerSide(winnerSide)
                .homeSetsWon(3).awaySetsWon(1).notPlayed(false).createExisting();
    }

    private static SetScore setScore(Game game, int setNumber) {
        return SetScore.builder().id(UUID.randomUUID()).game(game).setNumber(setNumber)
                .homePoints(11).awayPoints(5).build();
    }

    private static DoublesPair pair(Game game, String side, PlayerSeason player) {
        return DoublesPair.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .game(game).side(side).player(player).build();
    }
}
