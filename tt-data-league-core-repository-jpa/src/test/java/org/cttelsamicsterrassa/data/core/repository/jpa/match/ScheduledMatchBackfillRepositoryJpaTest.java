package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00078: exercises the backfill candidate query and write against a real (H2) database, through
 * the real repositories, so the mapper and the FEAT-00077 SCHEDULED invariant are proven end to end.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class ScheduledMatchBackfillRepositoryJpaTest {

    private static final Season SEASON = Season.of(2025);

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
    private PlayerSeasonRepository playerSeasonRepository;
    @Autowired
    private ScheduledMatchBackfillRepository backfillRepository;

    @Test
    void findsExactlyTheLegacyEmptyAndDecidedZeroZeroCandidatesWithTheirGameAndLineupCounts() {
        UUID legacyEmpty = legacyEmptyMatch().getId();
        UUID decidedZeroZero = decidedZeroZeroMatch().getId();
        playedWithWinnerMatch();
        playedDrawMatch();
        walkoverMatch();
        outOfScopeMatch(Season.of(2024), ImportSource.RFETM);
        outOfScopeMatch(SEASON, ImportSource.BCNESA);

        List<ScheduledMatchBackfillCandidate> candidates =
                backfillRepository.findScheduledBackfillCandidates(ImportSource.RFETM, SEASON);

        assertEquals(Set.of(legacyEmpty, decidedZeroZero), candidateIds(candidates));
        ScheduledMatchBackfillCandidate legacy = candidateById(candidates, legacyEmpty);
        assertEquals(0, legacy.gameCount());
        assertEquals(0, legacy.lineupCount());
        ScheduledMatchBackfillCandidate decided = candidateById(candidates, decidedZeroZero);
        assertEquals(7, decided.gameCount());
        assertEquals(3, decided.lineupCount());
    }

    @Test
    void writeMarksCandidatesScheduledAndReadsThemBackWithNoChildren() {
        UUID legacyEmpty = legacyEmptyMatch().getId();
        UUID decidedZeroZero = decidedZeroZeroMatch().getId();
        UUID playedWithWinner = playedWithWinnerMatch().getId();
        UUID playedDraw = playedDrawMatch().getId();
        UUID walkover = walkoverMatch().getId();

        ScheduledMatchBackfillWriteResult result = backfillRepository.markScheduled(
                ImportSource.RFETM, SEASON, List.of(legacyEmpty, decidedZeroZero));

        assertEquals(2, result.matchesUpdated());
        assertEquals(7, result.gamesDeleted());
        assertEquals(3, result.lineupsDeleted());
        assertEquals(0, result.setScoresDeleted());
        assertEquals(0, result.doublesPairsDeleted());

        assertScheduledWithNoChildren(legacyEmpty);
        assertScheduledWithNoChildren(decidedZeroZero);

        assertEquals(MatchStatus.PLAYED, matchRepository.findMatchById(playedWithWinner).orElseThrow().getStatus());
        assertEquals(MatchStatus.PLAYED, matchRepository.findMatchById(playedDraw).orElseThrow().getStatus());
        assertEquals(MatchStatus.PLAYED, matchRepository.findMatchById(walkover).orElseThrow().getStatus());

        List<ScheduledMatchBackfillCandidate> rerun =
                backfillRepository.findScheduledBackfillCandidates(ImportSource.RFETM, SEASON);
        assertTrue(rerun.isEmpty());
    }

    @Test
    void markScheduledWithAStaleIdThrowsAndLeavesEveryChildRowAndStatusIntact() {
        UUID legacyEmpty = legacyEmptyMatch().getId();
        UUID playedWithWinner = playedWithWinnerMatch().getId();

        assertThrows(IllegalStateException.class, () -> backfillRepository.markScheduled(
                ImportSource.RFETM, SEASON, List.of(legacyEmpty, playedWithWinner)));

        assertEquals(MatchStatus.PLAYED, matchRepository.findMatchById(legacyEmpty).orElseThrow().getStatus());
        Match winnerMatch = matchRepository.findMatchById(playedWithWinner).orElseThrow();
        assertEquals(MatchStatus.PLAYED, winnerMatch.getStatus());
        assertEquals(4, winnerMatch.getHomeGamesWon());
        assertEquals(2, gameRepository.findGamesByMatchId(playedWithWinner).size());
    }

    @Test
    void nullSourceOrSeasonFails() {
        assertThrows(NullPointerException.class,
                () -> backfillRepository.findScheduledBackfillCandidates(null, SEASON));
        assertThrows(NullPointerException.class,
                () -> backfillRepository.findScheduledBackfillCandidates(ImportSource.RFETM, null));
        assertThrows(NullPointerException.class,
                () -> backfillRepository.markScheduled(null, SEASON, List.of(UUID.randomUUID())));
        assertThrows(NullPointerException.class,
                () -> backfillRepository.markScheduled(ImportSource.RFETM, null, List.of(UUID.randomUUID())));
    }

    private void assertScheduledWithNoChildren(UUID matchId) {
        Match match = matchRepository.findMatchById(matchId).orElseThrow();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam());
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertNull(match.getHomeSetsWon());
        assertNull(match.getAwaySetsWon());
        assertNull(match.getSourceChecksum());
        assertTrue(gameRepository.findGamesByMatchId(matchId).isEmpty());
        assertTrue(lineupRepository.findLineupsByMatchId(matchId).isEmpty());
    }

    private static Set<UUID> candidateIds(List<ScheduledMatchBackfillCandidate> candidates) {
        return candidates.stream().map(ScheduledMatchBackfillCandidate::matchId).collect(Collectors.toSet());
    }

    private static ScheduledMatchBackfillCandidate candidateById(
            List<ScheduledMatchBackfillCandidate> candidates, UUID matchId) {
        return candidates.stream()
                .filter(candidate -> matchId.equals(candidate.matchId()))
                .findFirst()
                .orElseThrow();
    }

    // --- scenario builders -----------------------------------------------------------------

    /** (a) A legacy empty acta: null scores, no winner, no games, no lineups, with a stale checksum. */
    private Match legacyEmptyMatch() {
        Team home = storedTeam("LEGACY HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("LEGACY AWAY", SEASON, ImportSource.RFETM);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-femenino")
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .sourceChecksum("v1:legacy")
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);
        return match;
    }

    /** (b) A G17 "decided 0-0": header 0-0, no winner, home lineup present, every game not played. */
    private Match decidedZeroZeroMatch() {
        Team home = storedTeam("DECIDED HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("DECIDED AWAY", SEASON, ImportSource.RFETM);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-femenino")
                .season(SEASON)
                .groupNumber(1)
                .round(2)
                .homeGamesWon(0)
                .awayGamesWon(0)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);

        List<Game> games = List.of(
                notPlayedGame(match, 1, null),
                notPlayedGame(match, 2, null),
                notPlayedGame(match, 3, null),
                notPlayedGame(match, 4, null),
                notPlayedGame(match, 5, null),
                notPlayedGame(match, 6, null),
                notPlayedGame(match, 7, null));
        gameRepository.saveGames(games);

        lineupRepository.saveLineups(List.of(
                lineup(match, home, "A", 1, storedPlayerSeason("10001")),
                lineup(match, home, "B", 2, storedPlayerSeason("10002")),
                lineup(match, home, "C", 3, storedPlayerSeason("10003"))));

        return match;
    }

    /** (c) A real played match with a winner and games with set scores. */
    private Match playedWithWinnerMatch() {
        Team home = storedTeam("WINNER HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("WINNER AWAY", SEASON, ImportSource.RFETM);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-femenino")
                .season(SEASON)
                .groupNumber(1)
                .round(3)
                .homeTeam(home)
                .awayTeam(away)
                .winnerTeam(home)
                .homeGamesWon(4)
                .awayGamesWon(2)
                .homeSetsWon(13)
                .awaySetsWon(8)
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);

        Game gameOne = playedGame(match, 1, "HOME", 3, 1);
        Game gameTwo = playedGame(match, 2, "AWAY", 1, 3);
        gameRepository.saveGames(List.of(gameOne, gameTwo));
        setScoreRepository.saveSetScores(List.of(
                SetScore.builder().id(UUID.randomUUID()).game(gameOne).setNumber(1).homePoints(11).awayPoints(5).build(),
                SetScore.builder().id(UUID.randomUUID()).game(gameTwo).setNumber(1).homePoints(9).awayPoints(11).build()));

        return match;
    }

    /** (d) A played draw: no header winner, but individual games each have a winner. */
    private Match playedDrawMatch() {
        Team home = storedTeam("DRAW HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("DRAW AWAY", SEASON, ImportSource.RFETM);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-femenino")
                .season(SEASON)
                .groupNumber(1)
                .round(4)
                .homeGamesWon(3)
                .awayGamesWon(3)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);

        gameRepository.saveGames(List.of(
                playedGame(match, 1, "HOME", 3, 1),
                playedGame(match, 2, "AWAY", 1, 3)));

        return match;
    }

    /** (e) A walkover-style not_played game that still names a winner; must count as a result. */
    private Match walkoverMatch() {
        Team home = storedTeam("WALKOVER HOME", SEASON, ImportSource.RFETM);
        Team away = storedTeam("WALKOVER AWAY", SEASON, ImportSource.RFETM);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-femenino")
                .season(SEASON)
                .groupNumber(1)
                .round(5)
                .homeGamesWon(0)
                .awayGamesWon(0)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);

        gameRepository.saveGames(List.of(notPlayedGame(match, 1, "HOME")));

        return match;
    }

    /**
     * A candidate-shaped match (null everything, PLAYED, no children) outside the (source, season)
     * scope under test, to prove the rule never selects across scopes.
     */
    private Match outOfScopeMatch(Season season, ImportSource source) {
        Team home = storedTeam("OUT OF SCOPE HOME " + source + season, season, source);
        Team away = storedTeam("OUT OF SCOPE AWAY " + source + season, season, source);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition("divisio-honor-femenino")
                .season(season)
                .groupNumber(1)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.PLAYED)
                .createNew();
        matchRepository.saveMatch(match);
        return match;
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Team storedTeam(String name, Season season, ImportSource source) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, season, club);
        teamRepository.saveTeam(team);
        return team;
    }

    private PlayerSeason storedPlayerSeason(String license) {
        PlayerSeason playerSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "PLAYER " + license, license, null, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        return playerSeason;
    }

    private static Game notPlayedGame(Match match, int gameNumber, String winnerSide) {
        return Game.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .match(match)
                .gameNumber(gameNumber)
                .type("INDIVIDUAL")
                .crossover("")
                .winnerSide(winnerSide)
                .notPlayed(true)
                .reason("Victoria decidida (0-0)")
                .createNew();
    }

    private static Game playedGame(Match match, int gameNumber, String winnerSide, int homeSetsWon, int awaySetsWon) {
        return Game.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .match(match)
                .gameNumber(gameNumber)
                .type("INDIVIDUAL")
                .crossover("")
                .homeSetsWon(homeSetsWon)
                .awaySetsWon(awaySetsWon)
                .winnerSide(winnerSide)
                .notPlayed(false)
                .createNew();
    }

    private static Lineup lineup(Match match, Team team, String letter, int position, PlayerSeason player) {
        return Lineup.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .match(match)
                .team(team)
                .letter(letter)
                .position(position)
                .player(player)
                .createNew();
    }
}
