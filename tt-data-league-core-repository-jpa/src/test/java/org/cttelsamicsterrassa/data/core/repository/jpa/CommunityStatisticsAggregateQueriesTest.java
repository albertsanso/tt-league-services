package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the aggregate queries backing the community statistics overview (FEAT-00019), across
 * every source, independent of club or player consolidation.
 */
@SpringBootTest
@Transactional
class CommunityStatisticsAggregateQueriesTest {

    private static final Season CURRENT_SEASON = Season.of(2025);
    private static final Season PREVIOUS_SEASON = Season.of(2024);

    @Autowired
    private FederatedClubRepository clubRepository;

    @Autowired
    private FederatedPlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerSeasonRepository playerSeasonRepository;

    @Test
    void countsFederatedClubsAndPlayersAcrossEverySource() {
        clubRepository.saveFederatedClub(FederatedClub.createNew(ImportSource.RFETM, "CLUB A"));
        clubRepository.saveFederatedClub(FederatedClub.createNew(ImportSource.FCTT, "club a"));
        playerRepository.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.RFETM, "PLAYER A"));
        playerRepository.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.BCNESA, "player a"));

        assertEquals(1, clubRepository.countUniqueClubNames());
        assertEquals(1, playerRepository.countUniquePlayerNames());
    }

    @Test
    void findsTheMostRecentSeasonAndCountsMatchesWithinIt() {
        FederatedClub clubA = storedClub(ImportSource.RFETM, "CLUB A");
        FederatedClub clubB = storedClub(ImportSource.RFETM, "CLUB B");
        Team teamA = storedTeam(ImportSource.RFETM, "CLUB A", CURRENT_SEASON, clubA);
        Team teamB = storedTeam(ImportSource.RFETM, "CLUB B", CURRENT_SEASON, clubB);
        Team previousTeamA = storedTeam(ImportSource.RFETM, "CLUB A", PREVIOUS_SEASON, clubA);
        Team previousTeamB = storedTeam(ImportSource.RFETM, "CLUB B", PREVIOUS_SEASON, clubB);
        matchRepository.saveMatch(match(teamA, teamB, CURRENT_SEASON, 1));
        matchRepository.saveMatch(match(previousTeamA, previousTeamB, PREVIOUS_SEASON, 1));

        assertEquals(2, matchRepository.countAllMatches());
        assertEquals(List.of(CURRENT_SEASON.toString(), PREVIOUS_SEASON.toString()), matchRepository.findAllSeasons());
        assertEquals(1, matchRepository.countMatchesBySeason(CURRENT_SEASON));
    }

    @Test
    void countsDistinctFederatedClubsAndPlayersActiveInASeason() {
        FederatedClub clubA = storedClub(ImportSource.RFETM, "CLUB A");
        FederatedClub clubB = storedClub(ImportSource.RFETM, "CLUB B");
        // Two teams for the same club and season must count once.
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "CLUB A - I", CURRENT_SEASON, clubA));
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "CLUB A - II", CURRENT_SEASON, clubA));
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "CLUB B", CURRENT_SEASON, clubB));
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "CLUB A", PREVIOUS_SEASON, clubA));

        FederatedPlayer playerA = FederatedPlayer.createNew(ImportSource.RFETM, "PLAYER A");
        playerRepository.saveFederatedPlayer(playerA);
        playerSeasonRepository.savePlayerSeason(
                PlayerSeason.createNew(ImportSource.RFETM, "PLAYER A", "1", playerA, CURRENT_SEASON));
        playerSeasonRepository.savePlayerSeason(
                PlayerSeason.createNew(ImportSource.RFETM, "PLAYER A", "1", playerA, PREVIOUS_SEASON));

        assertEquals(2, teamRepository.countDistinctFederatedClubsBySeason(CURRENT_SEASON));
        assertEquals(1, playerSeasonRepository.countDistinctFederatedPlayersBySeason(CURRENT_SEASON));
    }

    private FederatedClub storedClub(ImportSource source, String name) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        return club;
    }

    private Team storedTeam(ImportSource source, String name, Season season, FederatedClub club) {
        Team team = Team.createExisting(UUID.randomUUID(), source, name, season, club);
        teamRepository.saveTeam(team);
        return team;
    }

    private static Match match(Team home, Team away, Season season, int round) {
        return Match.builder()
                .id(UUID.randomUUID())
                .competition("super-divisio-masculino")
                .season(season)
                .groupNumber(0)
                .round(round)
                .homeTeam(home)
                .awayTeam(away)
                .createNew();
    }
}
