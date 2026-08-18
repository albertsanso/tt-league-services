package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
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
    private ClubRepository clubRepository;

    @Autowired
    private ClubSeasonRepository clubSeasonRepository;

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
        Club club = Club.createNew(ImportSource.RFETM,"HORTITEC ALZIRA TT");
        clubRepository.saveClub(club);

        Optional<Club> found = clubRepository.findClubByName("HORTITEC ALZIRA TT");

        assertTrue(found.isPresent());
        assertEquals(club.getId(), found.get().getId());
        assertEquals("HORTITEC ALZIRA TT", found.get().getName());
    }

    @Test
    void keepsSameDisplayNameApartWhenTheSourceDiffers() {
        clubRepository.saveClub(Club.createNew(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF"));
        clubRepository.saveClub(Club.createNew(ImportSource.FCTT, "UNIVERSIDAD DE BURGOS - TPF"));

        UUID rfetm = clubRepository.findClubBySourceAndName(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF")
                .orElseThrow().getId();
        UUID fctt = clubRepository.findClubBySourceAndName(ImportSource.FCTT, "UNIVERSIDAD DE BURGOS - TPF")
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
        Club a = storedClub("790", "UNIVERSIDAD DE BURGOS - TPF A");
        Club b = storedClub("1056", "UNIVERSIDAD DE BURGOS - TPF B");
        clubSeasonRepository.saveClubSeason(ClubSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, a.getName(), SEASON, a));
        clubSeasonRepository.saveClubSeason(ClubSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, b.getName(), SEASON, b));

        ClubSeason forA = clubSeasonRepository.findClubSeasonByClubAndSeason(a.getId(), SEASON).orElseThrow();
        ClubSeason forB = clubSeasonRepository.findClubSeasonByClubAndSeason(b.getId(), SEASON).orElseThrow();

        assertNotEquals(forA.getId(), forB.getId());
    }

    @Test
    void storesEveryMatchOfARoundAndFindsThemByTheirNaturalKey() {
        ClubSeason a = storedClubSeason("1", "CLUB A");
        ClubSeason b = storedClubSeason("2", "CLUB B");
        ClubSeason c = storedClubSeason("3", "CLUB C");
        ClubSeason d = storedClubSeason("4", "CLUB D");

        matchRepository.saveMatch(match(a, b));
        matchRepository.saveMatch(match(c, d));

        Optional<Match> found = matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, c.getId(), d.getId());

        assertTrue(found.isPresent());
        assertEquals(c.getId(), found.get().getHomeClub().getId());
        assertEquals(1, found.get().getRound());
        assertTrue(matchRepository.findMatchByNaturalKey(
                "super-divisio-masculino", SEASON, 0, 1, a.getId(), d.getId()).isEmpty());
    }

    @Test
    void roundTripsAMatchThroughThePersistenceLayer() {
        ClubSeason home = storedClubSeason("1", "CLUB A");
        ClubSeason away = storedClubSeason("2", "CLUB B");
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
                .homeClub(home)
                .awayClub(away)
                .winnerClub(home)
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
        assertEquals(home.getId(), found.getWinnerClub().getId());
        assertEquals(4, found.getHomeGamesWon());
        assertTrue(found.isProtested());
    }

    private Club storedClub(String externalId, String name) {
        Club club = Club.createNew(ImportSource.RFETM, name);
        clubRepository.saveClub(club);
        return club;
    }

    private ClubSeason storedClubSeason(String externalId, String name) {
        Club club = storedClub(externalId, name);
        ClubSeason clubSeason = ClubSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        clubSeasonRepository.saveClubSeason(clubSeason);
        return clubSeason;
    }

    @Test
    void inventoriesClubSeasonsBySource() {
        storedClubSeason("1", "CLUB A");
        Club fcttClub = Club.createNew(ImportSource.FCTT, "CLUB A FCTT");
        clubRepository.saveClub(fcttClub);
        clubSeasonRepository.saveClubSeason(ClubSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "CLUB A", SEASON, fcttClub));

        assertEquals(1, clubSeasonRepository.findAllClubSeasonsBySource(ImportSource.FCTT).size());
        assertEquals(1, clubSeasonRepository.findAllClubSeasonsBySource(ImportSource.RFETM).size());
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
    void reassociatingAClubSeasonKeepsMatchAndLineupReferences() {
        Club original = storedClub("1", "ORIGINAL CLUB");
        Club canonical = storedClub("2", "CANONICAL CLUB");
        ClubSeason home = storedClubSeason(original, "HOME CLUB");
        ClubSeason away = storedClubSeason("3", "AWAY CLUB");
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
                .clubSeason(home)
                .letter("A")
                .position(1)
                .player(playerSeason)
                .createNew()));

        UUID homeId = home.getId();
        clubSeasonRepository.saveClubSeason(home.withClub(canonical));

        ClubSeason reloaded = clubSeasonRepository.findClubSeasonById(homeId).orElseThrow();
        assertEquals(canonical.getId(), reloaded.getClub().orElseThrow().getId());
        assertEquals(homeId, matchRepository.findMatchById(saved.getId()).orElseThrow().getHomeClub().getId());
        assertEquals(homeId, lineupRepository.findLineupsByMatchId(saved.getId()).getFirst().getClubSeason().getId());
    }

    private ClubSeason storedClubSeason(Club club, String name) {
        ClubSeason clubSeason = ClubSeason.createExisting(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
        clubSeasonRepository.saveClubSeason(clubSeason);
        return clubSeason;
    }

    private static Match match(ClubSeason home, ClubSeason away) {
        return Match.builder()
                .id(UUID.randomUUID())
                .competition("super-divisio-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .homeClub(home)
                .awayClub(away)
                .createNew();
    }
}
