package org.cttelsamicsterrassa.data.core.repository.jpa;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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
    void keepsTwoTeamsThatShareADisplayNameApart() {
        // One club fields several teams under one name; they are distinct rows keyed by team id.
        clubRepository.saveClub(Club.createNew(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF"));
        clubRepository.saveClub(Club.createNew(ImportSource.RFETM, "UNIVERSIDAD DE BURGOS - TPF"));

        UUID first = clubRepository.findAllClubsBySimilarName("UNIVERSIDAD").get(0).getId();
        UUID second = clubRepository.findAllClubsBySimilarName("UNIVERSIDAD").get(1).getId();

        assertNotEquals(first, second);
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
        Club a = storedClub("790", "UNIVERSIDAD DE BURGOS - TPF");
        Club b = storedClub("1056", "UNIVERSIDAD DE BURGOS - TPF");
        clubSeasonRepository.saveClubSeason(ClubSeason.of(UUID.randomUUID(), ImportSource.RFETM, a.getName(), SEASON, a));
        clubSeasonRepository.saveClubSeason(ClubSeason.of(UUID.randomUUID(), ImportSource.RFETM, b.getName(), SEASON, b));

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
        ClubSeason clubSeason = ClubSeason.of(UUID.randomUUID(), ImportSource.RFETM, name, SEASON, club);
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
