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
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.impl.TeamRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.impl.FederatedClubRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl.DoublesPairRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.game.impl.GameRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl.LineupRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.MatchRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.impl.PlayerSeasonRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.setscore.impl.SetScoreRepositoryHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00080 (risk K3): proves that a failed {@code replaceMatchContent} rolls back atomically —
 * the class is deliberately NOT {@code @Transactional} so the adapter's own transaction commits or
 * rolls back for real, and a fresh read afterwards sees either the full replacement or the original
 * match, never a half-written one.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
class MatchContentReplacementRollbackJpaTest {

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
    private PlayerSeasonRepository playerSeasonRepository;

    @Autowired
    private FederatedClubRepositoryHelper federatedClubRepositoryHelper;
    @Autowired
    private TeamRepositoryHelper teamRepositoryHelper;
    @Autowired
    private PlayerSeasonRepositoryHelper playerSeasonRepositoryHelper;
    @Autowired
    private MatchRepositoryHelper matchRepositoryHelper;
    @Autowired
    private LineupRepositoryHelper lineupRepositoryHelper;
    @Autowired
    private GameRepositoryHelper gameRepositoryHelper;
    @Autowired
    private SetScoreRepositoryHelper setScoreRepositoryHelper;
    @Autowired
    private DoublesPairRepositoryHelper doublesPairRepositoryHelper;

    @AfterEach
    void deleteEverything() {
        doublesPairRepositoryHelper.deleteAll();
        setScoreRepositoryHelper.deleteAll();
        gameRepositoryHelper.deleteAll();
        lineupRepositoryHelper.deleteAll();
        matchRepositoryHelper.deleteAll();
        playerSeasonRepositoryHelper.deleteAll();
        teamRepositoryHelper.deleteAll();
        federatedClubRepositoryHelper.deleteAll();
    }

    @Test
    void setScoreConstraintViolationRollsBackDeletesAndHeaderTogether() {
        Team home = storedTeam("RBK HOME");
        Team away = storedTeam("RBK AWAY");
        PlayerSeason anna = storedPlayerSeason("30001");
        Match original = seedPlayedContent(home, away, anna);
        UUID originalGameId = gameRepository.findGamesByMatchId(original.getId()).getFirst().getId();

        Game replacementGame = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(original).gameNumber(1).type("INDIVIDUAL").crossover("")
                .winnerSide("HOME").homeSetsWon(3).awaySetsWon(1).notPlayed(false).createNew();
        MatchContent violating = new MatchContent(headerOf(original, home, away),
                List.of(), List.of(replacementGame),
                List.of(setScore(replacementGame, 1, 11, 5), setScore(replacementGame, 1, 9, 11)),
                List.of());

        assertThrows(RuntimeException.class, () -> matchRepository.replaceMatchContent(violating));

        Match after = matchRepository.findMatchById(original.getId()).orElseThrow();
        assertEquals(MatchStatus.PLAYED, after.getStatus());
        assertEquals(5, after.getHomeGamesWon());
        List<Game> games = gameRepository.findGamesByMatchId(original.getId());
        assertEquals(1, games.size());
        assertEquals(originalGameId, games.getFirst().getId());
        assertEquals(1, setScoreRepository.findSetScoresByGameIds(List.of(originalGameId)).size());
        assertEquals(1, lineupRepository.findLineupsByMatchId(original.getId()).size());
    }

    @Test
    void oversizedHeaderFieldRollsBackTheWholeReplacement() {
        Team home = storedTeam("RB2 HOME");
        Team away = storedTeam("RB2 AWAY");
        PlayerSeason anna = storedPlayerSeason("30002");
        Match original = seedPlayedContent(home, away, anna);

        Match badHeader = Match.builder().id(original.getId()).source(ImportSource.RFETM)
                .externalId("X".repeat(25)).competition(COMPETITION).season(SEASON)
                .groupNumber(1).round(1).homeTeam(home).awayTeam(away)
                .homeGamesWon(4).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();

        assertThrows(RuntimeException.class, () -> matchRepository.replaceMatchContent(
                new MatchContent(badHeader, List.of(), List.of(), List.of(), List.of())));

        Match after = matchRepository.findMatchById(original.getId()).orElseThrow();
        assertEquals(5, after.getHomeGamesWon());
        assertEquals("RB-1", after.getExternalId());
        assertEquals(1, gameRepository.findGamesByMatchId(original.getId()).size());
        assertEquals(1, lineupRepository.findLineupsByMatchId(original.getId()).size());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match seedPlayedContent(Team home, Team away, PlayerSeason anna) {
        Match match = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .externalId("RB-1").competition(COMPETITION).season(SEASON)
                .groupNumber(1).round(1).homeTeam(home).awayTeam(away)
                .homeGamesWon(5).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createNew();
        matchRepository.saveMatch(match);
        Game game = Game.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).match(match)
                .gameNumber(1).type("INDIVIDUAL").crossover("").winnerSide("HOME")
                .homeSetsWon(3).awaySetsWon(1).notPlayed(false).createNew();
        gameRepository.saveGames(List.of(game));
        setScoreRepository.saveSetScores(List.of(setScore(game, 1, 11, 4)));
        lineupRepository.saveLineups(List.of(Lineup.builder().id(UUID.randomUUID())
                .source(ImportSource.RFETM).match(match).team(home).letter("A").position(1)
                .player(anna).createNew()));
        return match;
    }

    private Match headerOf(Match original, Team home, Team away) {
        return Match.builder().id(original.getId()).source(ImportSource.RFETM)
                .externalId(original.getExternalId()).competition(COMPETITION).season(SEASON)
                .groupNumber(1).round(1).homeTeam(home).awayTeam(away)
                .homeGamesWon(4).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private static SetScore setScore(Game game, int setNumber, int homePoints, int awayPoints) {
        return SetScore.builder().id(UUID.randomUUID()).game(game).setNumber(setNumber)
                .homePoints(homePoints).awayPoints(awayPoints).build();
    }

    private Team storedTeam(String name) {
        FederatedClub club = FederatedClub.createNew(ImportSource.RFETM, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }

    private PlayerSeason storedPlayerSeason(String license) {
        PlayerSeason playerSeason = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "PLAYER " + license, license, null, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        return playerSeason;
    }
}
