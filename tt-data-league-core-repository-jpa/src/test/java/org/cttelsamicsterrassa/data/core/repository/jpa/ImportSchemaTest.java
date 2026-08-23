package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the schema decisions the RFETM import depends on, against a real database.
 */
@SpringBootTest
@Transactional
class ImportSchemaTest {

    private static final Season SEASON = Season.of(2023);

    @Autowired
    private FederatedClubRepository clubRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSeasonRepository playerSeasonRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Test
    void findsAClubByItsFederationId() {
        FederatedClub club = FederatedClub.createNew(ImportSource.RFETM,"HORTITEC ALZIRA TT");
        clubRepository.saveFederatedClub(club);

        Optional<FederatedClub> found = clubRepository.findFederatedClubBySourceAndName(
                ImportSource.RFETM, "HORTITEC ALZIRA TT");

        assertTrue(found.isPresent());
        assertEquals(club.getId(), found.get().getId());
        assertEquals("HORTITEC ALZIRA TT", found.get().getName());
    }

    @Test
    void keepsSameDisplayNameApartWhenTheSourceDiffers() {
        clubRepository.saveFederatedClub(FederatedClub.createNew(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF"));
        clubRepository.saveFederatedClub(FederatedClub.createNew(ImportSource.FCTT, "UNIVERSIDAD DE BURGOS - TPF"));

        UUID rfetm = clubRepository.findFederatedClubBySourceAndName(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF")
                .orElseThrow().getId();
        UUID fctt = clubRepository.findFederatedClubBySourceAndName(ImportSource.FCTT, "UNIVERSIDAD DE BURGOS - TPF")
                .orElseThrow().getId();

        assertNotEquals(rfetm, fctt);
    }

    @Test
    void findsAPlayerByItsSourceScopedFederationId() {
        Player rfetm = Player.createNew(ImportSource.RFETM, "RFETM PLAYER");
        Player bcnesa = Player.createNew(ImportSource.BCNESA, "BCNESA PLAYER");
        playerRepository.savePlayer(rfetm);
        playerRepository.savePlayer(bcnesa);

        Player found = playerRepository.findPlayerBySourceAndName(ImportSource.BCNESA, "BCNESA PLAYER").orElseThrow();

        assertEquals(bcnesa.getId(), found.getId());
        assertEquals(ImportSource.BCNESA, found.getSource());
    }

    @Test
    void keepsASeasonEntryPerClubEvenWhenTheNamesCollide() {
        FederatedClub a = storedClub("790", "UNIVERSIDAD DE BURGOS - TPF A");
        FederatedClub b = storedClub("1056", "UNIVERSIDAD DE BURGOS - TPF B");
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, a.getName(), SEASON, a));
        teamRepository.saveTeam(Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, b.getName(), SEASON, b));

        Team forA = teamRepository.findTeamByFederatedClubAndSeason(a.getId(), SEASON).orElseThrow();
        Team forB = teamRepository.findTeamByFederatedClubAndSeason(b.getId(), SEASON).orElseThrow();

        assertNotEquals(forA.getId(), forB.getId());
    }

    @Test
    void storesEveryMatchOfARoundAndFindsThemByTheirNaturalKey() {
        Team a = storedTeam("1", "CLUB A");
        Team b = storedTeam("2", "CLUB B");
        Team c = storedTeam("3", "CLUB C");
        Team d = storedTeam("4", "CLUB D");

        matchRepository.saveMatch(match(a, b));
        matchRepository.saveMatch(match(c, d));

        Optional<Match> found = matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, c.getId(), d.getId());

        assertTrue(found.isPresent());
        assertEquals(c.getId(), found.get().getHomeTeam().getId());
        assertEquals(1, found.get().getRound());
        assertTrue(matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, a.getId(), d.getId()).isEmpty());
    }

    @Test
    void roundTripsAMatchThroughThePersistenceLayer() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .externalId("match-123")
                .competition("super-divisio-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(7)
                .dateTime(ZonedDateTime.of(2023, 9, 29, 19, 0, 0, 0, Match.COMPETITION_ZONE))
                .city("Alzira (Valencia)")
                .venue("PABELLON PEREZ PUIG")
                .homeTeam(home)
                .awayTeam(away)
                .winnerTeam(home)
                .refereeName("A REFEREE")
                .homeGamesWon(4)
                .awayGamesWon(2)
                .homeSetsWon(13)
                .awaySetsWon(8)
                .protested(true)
                .createNew();
        matchRepository.saveMatch(saved);

        Match found = matchRepository.findMatchById(saved.getId()).orElseThrow();

        assertEquals(saved.getId(), matchRepository.findMatchByExternalId("match-123").orElseThrow().getId());
        assertEquals(7, found.getRound());
        assertEquals(ImportSource.RFETM, found.getSource());
        assertEquals("match-123", found.getExternalId());
        assertEquals(saved.getDateTime(), found.getDateTime());
        assertEquals(home.getId(), found.getWinnerTeam().getId());
        assertEquals(4, found.getHomeGamesWon());
        assertTrue(found.isProtested());
    }

    private FederatedClub storedClub(String externalId, String name) {
        FederatedClub club = FederatedClub.createNew(ImportSource.RFETM, name);
        clubRepository.saveFederatedClub(club);
        return club;
    }

    private Team storedTeam(String externalId, String name) {
        FederatedClub club = storedClub(externalId, name);
        Team team = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }

    @Test
    void inventoriesTeamsBySource() {
        storedTeam("1", "CLUB A");
        FederatedClub fcttClub = FederatedClub.createNew(ImportSource.FCTT, "CLUB A FCTT");
        clubRepository.saveFederatedClub(fcttClub);
        teamRepository.saveTeam(Team.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "CLUB A", SEASON, fcttClub));

        assertEquals(1, teamRepository.findAllTeamsBySource(ImportSource.FCTT).size());
        assertEquals(1, teamRepository.findAllTeamsBySource(ImportSource.RFETM).size());
    }

    @Test
    void loadsClubRegistrationsAndMatchesByCanonicalTeamIds() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = match(home, away);
        matchRepository.saveMatch(saved);

        assertEquals(List.of(home.getId()),
                teamRepository.findAllTeamsByFederatedClubId(home.getFederatedClub().orElseThrow().getId())
                        .stream().map(Team::getId).toList());
        assertEquals(List.of(saved.getId()),
                matchRepository.findAllMatchesByTeamIds(List.of(home.getId()))
                        .stream().map(Match::getId).toList());
    }

    @Test
    void roundTripsPlayerSeasonWithAndWithoutPlayerAssociation() {
        PlayerSeason unassigned = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "UNASSIGNED PLAYER", "unassigned", null, SEASON);
        playerSeasonRepository.savePlayerSeason(unassigned);

        Player player = Player.createNew(ImportSource.RFETM, "ASSIGNED PLAYER");
        playerRepository.savePlayer(player);
        PlayerSeason assigned = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "ASSIGNED PLAYER", "assigned", player, SEASON);
        playerSeasonRepository.savePlayerSeason(assigned);

        PlayerSeason reloadedUnassigned = playerSeasonRepository
                .findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, "unassigned", SEASON)
                .orElseThrow();
        PlayerSeason reloadedAssigned = playerSeasonRepository
                .findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, "assigned", SEASON)
                .orElseThrow();

        assertTrue(reloadedUnassigned.getPlayer().isEmpty());
        assertEquals(unassigned.getId(), reloadedUnassigned.getId());
        assertEquals(assigned.getId(), reloadedAssigned.getId());
        assertEquals(player.getId(), reloadedAssigned.getPlayer().orElseThrow().getId());
        assertEquals("assigned", reloadedAssigned.getLicense());
        assertEquals(SEASON, reloadedAssigned.getSeason());
    }

    @Test
    void findsCompetitionsForPlayerSeasonsFromTheirLineups() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = match(home, away);
        matchRepository.saveMatch(saved);

        Player player = Player.createNew(ImportSource.RFETM, "PLAYER, ONE");
        playerRepository.savePlayer(player);
        PlayerSeason playerSeason = PlayerSeason.createNew(
                ImportSource.RFETM, "PLAYER, ONE", "1", player, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        lineupRepository.saveLineups(List.of(Lineup.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .match(saved)
                .team(home)
                .letter("A")
                .position(1)
                .player(playerSeason)
                .createNew()));

        assertEquals(
                List.of("super-divisio-masculino"),
                playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
                        List.of(home.getId()), ImportSource.RFETM).get(playerSeason.getId()));
    }

    @Test
    void reassociatingATeamKeepsMatchAndLineupReferences() {
        FederatedClub original = storedClub("1", "ORIGINAL CLUB");
        FederatedClub canonical = storedClub("2", "CANONICAL CLUB");
        Team home = storedTeam(original, "HOME CLUB");
        Team away = storedTeam("3", "AWAY CLUB");
        Match saved = match(home, away);
        matchRepository.saveMatch(saved);

        Player player = Player.createNew(ImportSource.RFETM, "PLAYER, ONE");
        playerRepository.savePlayer(player);
        PlayerSeason playerSeason = PlayerSeason.createNew(ImportSource.RFETM, "PLAYER, ONE", "1", player, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        lineupRepository.saveLineups(List.of(Lineup.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .match(saved)
                .team(home)
                .letter("A")
                .position(1)
                .player(playerSeason)
                .createNew()));

        UUID homeId = home.getId();
        teamRepository.saveTeam(home.withFederatedClub(canonical));

        Team reloaded = teamRepository.findTeamById(homeId).orElseThrow();
        assertEquals(canonical.getId(), reloaded.getFederatedClub().orElseThrow().getId());
        assertEquals(homeId, matchRepository.findMatchById(saved.getId()).orElseThrow().getHomeTeam().getId());
        assertEquals(homeId, lineupRepository.findLineupsByMatchId(saved.getId()).getFirst().getTeam().getId());
    }

    private Team storedTeam(FederatedClub club, String name) {
        Team team = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }

    private static Match match(Team home, Team away) {
        return Match.builder()
                .id(UUID.randomUUID())
                .competition("super-divisio-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .createNew();
    }
}
