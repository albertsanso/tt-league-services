package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSearchCriteria;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the schema decisions the RFETM import depends on, against a real database.
 */
@SpringBootTest
@Transactional
class ImportSchemaTest {

    private static final Season SEASON = Season.of(2023);

    @Test
    void validatesCanonicalClubNamesAndKeepsFederatedNameIndependent() {
        assertThrows(NullPointerException.class, () -> Club.createNew(null));
        assertThrows(IllegalArgumentException.class, () -> Club.createNew(" \t"));

        Club canonical = Club.createNew("CANONICAL ORIGINAL");
        FederatedClub federated = FederatedClub.createNew(
                ImportSource.FCTT, "SOURCE DISPLAY NAME", canonical);

        canonical.modifyName("CANONICAL RENAMED");

        assertEquals("CANONICAL RENAMED", federated.getClub().orElseThrow().getName());
        assertEquals("SOURCE DISPLAY NAME", federated.getName());
    }

    @Autowired
    private FederatedClubRepository clubRepository;

    @Autowired
    private ClubRepository canonicalClubRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private FederatedPlayerRepository playerRepository;

    @Autowired
    private PlayerRepository canonicalPlayerRepository;

    @Autowired
    private EntityManager entityManager;

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
    void roundTripsCanonicalClubAndFederatedAssociation() {
        Club canonical = Club.createNew("CANONICAL CLUB");
        canonicalClubRepository.saveClub(canonical);
        FederatedClub federated = FederatedClub.createNew(
                ImportSource.FCTT, "CANONICAL CLUB", canonical);
        clubRepository.saveFederatedClub(federated);

        FederatedClub found = clubRepository.findFederatedClubById(federated.getId()).orElseThrow();

        assertEquals(canonical.getId(), found.getClub().orElseThrow().getId());
        assertEquals("CANONICAL CLUB", found.getClub().orElseThrow().getName());
        assertEquals(canonical.getId(),
                canonicalClubRepository.findClubByExactName("CANONICAL CLUB").orElseThrow().getId());
    }

    @Test
    void preservesCanonicalAssociationWhenSavingASeasonTeam() {
        Club canonical = Club.createNew("TEAM CANONICAL CLUB");
        canonicalClubRepository.saveClub(canonical);
        FederatedClub federated = FederatedClub.createNew(
                ImportSource.FCTT, "TEAM SOURCE CLUB", canonical);
        clubRepository.saveFederatedClub(federated);
        Team team = Team.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "TEAM SOURCE CLUB", SEASON, federated);

        teamRepository.saveTeam(team);

        Team reloaded = teamRepository.findTeamById(team.getId()).orElseThrow();
        assertEquals(canonical.getId(), reloaded.getFederatedClub().orElseThrow()
                .getClub().orElseThrow().getId());
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
        FederatedPlayer rfetm = FederatedPlayer.createNew(ImportSource.RFETM, "RFETM PLAYER");
        FederatedPlayer bcnesa = FederatedPlayer.createNew(ImportSource.BCNESA, "BCNESA PLAYER");
        playerRepository.saveFederatedPlayer(rfetm);
        playerRepository.saveFederatedPlayer(bcnesa);

        FederatedPlayer found = playerRepository.findFederatedPlayerBySourceAndName(ImportSource.BCNESA, "BCNESA PLAYER").orElseThrow();

        assertEquals(bcnesa.getId(), found.getId());
        assertEquals(ImportSource.BCNESA, found.getSource());
    }

    @Test
    void findsAPlayerByItsSourceScopedLicenseId() {
        FederatedPlayer rfetm = FederatedPlayer.createNew(ImportSource.RFETM, "RFETM PLAYER", "LIC-1");
        FederatedPlayer bcnesa = FederatedPlayer.createNew(ImportSource.BCNESA, "BCNESA PLAYER", "LIC-1");
        playerRepository.saveFederatedPlayer(rfetm);
        playerRepository.saveFederatedPlayer(bcnesa);

        FederatedPlayer found =
                playerRepository.findFederatedPlayerBySourceAndLicenseId(ImportSource.BCNESA, "LIC-1").orElseThrow();

        assertEquals(bcnesa.getId(), found.getId());
        assertEquals(ImportSource.BCNESA, found.getSource());
    }

    @Test
    void roundTripsCanonicalPlayerAndFederatedAssociation() {
        Player canonical = Player.createNew("CANONICAL PLAYER");
        canonicalPlayerRepository.savePlayer(canonical);
        FederatedPlayer federated = FederatedPlayer.createNew(
                ImportSource.FCTT, "SOURCE PLAYER", canonical);
        playerRepository.saveFederatedPlayer(federated);

        FederatedPlayer found = playerRepository.findFederatedPlayerById(federated.getId()).orElseThrow();

        assertEquals(canonical.getId(), found.getPlayer().orElseThrow().getId());
        assertEquals("CANONICAL PLAYER", found.getPlayer().orElseThrow().getName());
        assertEquals(canonical.getId(),
                canonicalPlayerRepository.findPlayerByExactName("CANONICAL PLAYER").orElseThrow().getId());
    }

    @Test
    void enforcesCanonicalPlayerNameUniqueness() {
        canonicalPlayerRepository.savePlayer(Player.createNew("UNIQUE CANONICAL PLAYER"));

        canonicalPlayerRepository.savePlayer(Player.createNew("UNIQUE CANONICAL PLAYER"));

        assertThrows(Exception.class, entityManager::flush);
    }

    @Test
    void rejectsAmbiguousSourceScopedPlayerNameResolutionWithoutAUniqueConstraint() {
        playerRepository.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.RFETM, "DUPLICATE PLAYER"));
        playerRepository.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.RFETM, "DUPLICATE PLAYER"));

        assertThrows(IllegalStateException.class, () ->
                playerRepository.findFederatedPlayerBySourceAndName(
                        ImportSource.RFETM, "DUPLICATE PLAYER"));
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
                "super-divisio-masculino", SEASON, 0, 1, null, c.getId(), d.getId());

        assertTrue(found.isPresent());
        assertEquals(c.getId(), found.get().getHomeTeam().getId());
        assertEquals(1, found.get().getRound());
        assertTrue(matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, null, a.getId(), d.getId()).isEmpty());
    }

    @Test
    void distinguishesMatchesSharingARoundButBelongingToDifferentPhases() {
        Team a = storedTeam("1", "CLUB A");
        Team b = storedTeam("2", "CLUB B");

        Match firstPhase = Match.builder()
                .id(UUID.randomUUID())
                .competition("super-divisio-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .phase("1a Fase")
                .homeTeam(a)
                .awayTeam(b)
                .createNew();
        Match secondPhase = Match.builder()
                .id(UUID.randomUUID())
                .competition("super-divisio-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .phase("2a Fase")
                .homeTeam(a)
                .awayTeam(b)
                .createNew();

        matchRepository.saveMatch(firstPhase);
        matchRepository.saveMatch(secondPhase);

        Optional<Match> foundFirst = matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, "1a Fase", a.getId(), b.getId());
        Optional<Match> foundSecond = matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, "2a Fase", a.getId(), b.getId());

        assertTrue(foundFirst.isPresent());
        assertTrue(foundSecond.isPresent());
        assertEquals(firstPhase.getId(), foundFirst.get().getId());
        assertEquals(secondPhase.getId(), foundSecond.get().getId());

        assertTrue(matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, "1a Fase", a.getId(), b.getId()).isPresent());
    }

    @Test
    void persistsAndDedupesAMatchWithANullGroupNumber() {
        Team a = storedTeam("1", "CLUB A");
        Team b = storedTeam("2", "CLUB B");

        Match otherPhase = Match.builder()
                .id(UUID.randomUUID())
                .competition("veterans")
                .season(SEASON)
                .groupNumber(null)
                .round(1)
                .phase("Play Off")
                .homeTeam(a)
                .awayTeam(b)
                .createNew();
        matchRepository.saveMatch(otherPhase);

        Optional<Match> found = matchRepository.findMatchByNaturalKey(
                "veterans", SEASON, null, 1, "Play Off", a.getId(), b.getId());

        assertTrue(found.isPresent());
        assertNull(found.get().getGroupNumber());
        assertTrue(matchRepository.findMatchByNaturalKey(
                "veterans", SEASON, 0, 1, "Play Off", a.getId(), b.getId()).isEmpty(),
                "a numbered group must not match a null-group fixture");
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
                .phase("1a Fase")
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
        assertEquals("1a Fase", found.getPhase());
    }

    @Test
    void matchWithoutAPhaseIsStoredAndReadBackAsNull() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = match(home, away);
        matchRepository.saveMatch(saved);

        assertNull(matchRepository.findMatchById(saved.getId()).orElseThrow().getPhase());
    }

    @Test
    void searchMatchesWithoutACompetitionFilterReturnsMatchesAcrossCompetitions() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match preferent = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition("Preferent")
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .createNew();
        Match primera = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition("Primera")
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(away)
                .awayTeam(home)
                .createNew();
        matchRepository.saveMatch(preferent);
        matchRepository.saveMatch(primera);

        MatchSearchCriteria withoutCompetition = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, null,
                null, null, null, null, null, null, 0, 10);
        assertEquals(2, matchRepository.searchMatches(withoutCompetition).size());
        assertEquals(2, matchRepository.countMatches(withoutCompetition));

        MatchSearchCriteria withCompetition = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, null, null, 0, 10);
        List<Match> results = matchRepository.searchMatches(withCompetition);
        assertEquals(1, results.size());
        assertEquals(preferent.getId(), results.getFirst().getId());
    }

    @Test
    void searchMatchesFiltersByClubNameCaseInsensitivelyAndByAnyFragment() {
        Team terrassa = storedTeam("1", "CN Terrassa A");
        Team manresa = storedTeam("2", "UE Manresa B");
        Team alien = storedTeam("3", "CLUB ALIEN");
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition("Preferent")
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(terrassa)
                .awayTeam(manresa)
                .createNew();
        Match otherMatch = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition("Preferent")
                .season(SEASON)
                .groupNumber(1)
                .round(2)
                .homeTeam(alien)
                .awayTeam(manresa)
                .createNew();
        matchRepository.saveMatch(match);
        matchRepository.saveMatch(otherMatch);

        MatchSearchCriteria unfiltered = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, null, null, 0, 10);
        assertEquals(2, matchRepository.searchMatches(unfiltered).size());
        assertEquals(2, matchRepository.countMatches(unfiltered));

        MatchSearchCriteria singleFragment = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, null, "terrassa", 0, 10);
        assertEquals(1, matchRepository.searchMatches(singleFragment).size());

        // Multi-word search matches ANY fragment, not the whole phrase: "terrassa" only matches the
        // first match's home team, "alien" only matches the second match's home team, so both come back.
        MatchSearchCriteria anyFragment = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, null, "terrassa alien", 0, 10);
        List<Match> results = matchRepository.searchMatches(anyFragment);
        assertEquals(2, results.size());
        assertEquals(2, matchRepository.countMatches(anyFragment));

        MatchSearchCriteria noMatch = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, null, "nonexistent", 0, 10);
        assertEquals(0, matchRepository.searchMatches(noMatch).size());
        assertEquals(0, matchRepository.countMatches(noMatch));
    }

    @Test
    void searchMatchesFiltersByPlayerNameCaseInsensitivelyAndByAnyFragment() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .competition("Preferent")
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .homeTeam(home)
                .awayTeam(away)
                .createNew();
        matchRepository.saveMatch(saved);

        FederatedPlayer federatedPlayer = FederatedPlayer.createNew(ImportSource.BCNESA, "CAMPOS, OSCAR");
        playerRepository.saveFederatedPlayer(federatedPlayer);
        PlayerSeason playerSeason = PlayerSeason.createNew(
                ImportSource.BCNESA, "CAMPOS, OSCAR", "license-1", federatedPlayer, SEASON);
        playerSeasonRepository.savePlayerSeason(playerSeason);
        lineupRepository.saveLineups(List.of(Lineup.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.BCNESA)
                .match(saved)
                .team(home)
                .letter("A")
                .position(1)
                .player(playerSeason)
                .createNew()));

        MatchSearchCriteria byFullName = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, "oscar campos", null, 0, 10);
        assertEquals(1, matchRepository.searchMatches(byFullName).size());

        // Fragment order doesn't matter, and an extra fragment that matches nothing is ignored,
        // because matching is ANY fragment, not the whole phrase.
        MatchSearchCriteria byReorderedFragments = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, "campos oscar nonexistent", null, 0, 10);
        List<Match> results = matchRepository.searchMatches(byReorderedFragments);
        assertEquals(1, results.size());
        assertEquals(saved.getId(), results.getFirst().getId());
        assertEquals(1, matchRepository.countMatches(byReorderedFragments));

        MatchSearchCriteria noMatch = new MatchSearchCriteria(ImportSource.BCNESA, SEASON, "Preferent",
                null, null, null, null, "nonexistent alsomissing", null, 0, 10);
        assertEquals(0, matchRepository.searchMatches(noMatch).size());
        assertEquals(0, matchRepository.countMatches(noMatch));
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

        FederatedPlayer player = FederatedPlayer.createNew(ImportSource.RFETM, "ASSIGNED PLAYER");
        playerRepository.saveFederatedPlayer(player);
        PlayerSeason assigned = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "ASSIGNED PLAYER", "assigned", player, SEASON);
        playerSeasonRepository.savePlayerSeason(assigned);

        PlayerSeason reloadedUnassigned = playerSeasonRepository
                .findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, "unassigned", SEASON)
                .orElseThrow();
        PlayerSeason reloadedAssigned = playerSeasonRepository
                .findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, "assigned", SEASON)
                .orElseThrow();

        assertTrue(reloadedUnassigned.getFederatedPlayer().isEmpty());
        assertEquals(unassigned.getId(), reloadedUnassigned.getId());
        assertEquals(assigned.getId(), reloadedAssigned.getId());
        assertEquals(player.getId(), reloadedAssigned.getFederatedPlayer().orElseThrow().getId());
        assertEquals("assigned", reloadedAssigned.getLicense());
        assertEquals(SEASON, reloadedAssigned.getSeason());
    }

    @Test
    void findsCompetitionsForPlayerSeasonsFromTheirLineups() {
        Team home = storedTeam("1", "CLUB A");
        Team away = storedTeam("2", "CLUB B");
        Match saved = match(home, away);
        matchRepository.saveMatch(saved);

        FederatedPlayer player = FederatedPlayer.createNew(ImportSource.RFETM, "PLAYER, ONE");
        playerRepository.saveFederatedPlayer(player);
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

        FederatedPlayer player = FederatedPlayer.createNew(ImportSource.RFETM, "PLAYER, ONE");
        playerRepository.saveFederatedPlayer(player);
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
