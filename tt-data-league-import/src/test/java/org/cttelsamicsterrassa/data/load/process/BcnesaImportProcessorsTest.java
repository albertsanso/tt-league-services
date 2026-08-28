package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the three BCNESA processors in their declared order over a real two-fixture matchday report,
 * the way the navigator would after splitting it.
 */
class BcnesaImportProcessorsTest {

    private InMemoryRepositories.Clubs clubs;
    private InMemoryRepositories.CanonicalClubs canonicalClubs;
    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.Players players;
    private InMemoryRepositories.Players.CanonicalPlayers canonicalPlayers;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.DoublesPairs doublesPairs;

    private List<BcnesaMatchReportProcessor> processors;
    private Acta acta;

    @BeforeEach
    void setUp() {
        clubs = new InMemoryRepositories.Clubs();
        canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        teams = new InMemoryRepositories.Teams();
        players = new InMemoryRepositories.Players();
        canonicalPlayers = new InMemoryRepositories.Players.CanonicalPlayers();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        matches = new InMemoryRepositories.Matches();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        doublesPairs = new InMemoryRepositories.DoublesPairs();

        processors = List.of(
                new BcnesaTeamImportProcessor(teams),
                new BcnesaPlayerImportProcessor(playerSeasons),
                new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        doublesPairs));

        acta = new ActaParser().parse(fixture("acta_matchday.json"));
    }

    @Test
    void storesBothClubsOfEachFixtureUnderTheBcnesaSource() {
        run(firstFixture());
        run(secondFixture());

        assertEquals(4, clubs.byId.size());
        assertEquals(4, canonicalClubs.size());
        FederatedClub home1 = clubs.findFederatedClubBySourceAndName(ImportSource.BCNESA, "FALCONS DE SABADELL").orElseThrow();
        FederatedClub home2 = clubs.findFederatedClubBySourceAndName(ImportSource.BCNESA, "CTT ATENEU").orElseThrow();
        assertEquals("FALCONS DE SABADELL", home1.getClub().orElseThrow().getName());
        assertEquals("CTT ATENEU", home2.getClub().orElseThrow().getName());
        assertTrue(teams.findTeamByFederatedClubAndSeason(home1.getId(), Season.of(2020)).isPresent());
        assertTrue(teams.findTeamByFederatedClubAndSeason(home2.getId(), Season.of(2020)).isPresent());
    }

    @Test
    void normalizesQuotedTeamLetterSuffixesToOneClubRow() {
        BcnesaMatchReportContext quoted = fixtureContext(0, "CLUB ARIEL \"A\"", "CLUB ARIEL ''B''");
        BcnesaMatchReportContext bare = fixtureContext(1, "CLUB ARIEL A", "CLUB ARIEL B");

        run(quoted);
        run(bare);

        assertEquals(2, clubs.byId.size());
        assertTrue(clubs.findFederatedClubBySourceAndName(ImportSource.BCNESA, "CLUB ARIEL A").isPresent());
        assertTrue(clubs.findFederatedClubBySourceAndName(ImportSource.BCNESA, "CLUB ARIEL B").isPresent());
    }

    @Test
    void storesOnePlayerPerSinglesParticipantAcrossBothFixtures() {
        run(firstFixture());
        run(secondFixture());

        // 2 games per fixture, 2 distinct participants per game, no overlap between fixtures.
        assertEquals(8, players.byId.size());
        assertEquals(8, canonicalPlayers.byId.size());
        assertEquals(8, playerSeasons.byId.size());
        assertTrue(playerSeasons.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.BCNESA, "7026", Season.of(2020)).isPresent());
        assertTrue(playerSeasons.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.BCNESA, "878", Season.of(2020)).isPresent());
    }

    @Test
    void storesEachFixtureAsItsOwnMatchWithScoresDerivedFromItsOwnGames() {
        run(firstFixture());
        run(secondFixture());

        assertEquals(2, matches.saved.size());
        Match first = matches.saved.stream()
                .filter(m -> "FALCONS DE SABADELL".equals(homeName(m)))
                .findFirst().orElseThrow();
        assertEquals("Preferent", first.getCompetition());
        assertEquals(Season.of(2020), first.getSeason());
        assertEquals(1, first.getGroupNumber());
        assertEquals(7, first.getRound());
        assertEquals(1, first.getHomeGamesWon());
        assertEquals(1, first.getAwayGamesWon());
        // Tied on games, so no club won this fixture.
        assertNull(first.getWinnerTeam());

        Match second = matches.saved.stream()
                .filter(m -> "CTT ATENEU".equals(homeName(m)))
                .findFirst().orElseThrow();
        assertEquals(2, second.getHomeGamesWon());
        assertEquals(0, second.getAwayGamesWon());
        assertEquals(second.getHomeTeam(), second.getWinnerTeam());
    }

    @Test
    void storesLineupsFromTheFixturesOwnGamesRatherThanFileLevelAlineaciones() {
        run(secondFixture());

        // The second fixture's players never appear in the file's alineaciones (only the first
        // fixture's do), yet the lineup is still populated from the fixture's own games.
        assertEquals(4, lineups.saved.size());
        assertTrue(lineups.saved.stream().anyMatch(l -> "A".equals(l.getLetter())));
    }

    @Test
    void dedupsTheDuplicatedDoublesNameIntoOnePairMember() {
        run(doublesFixture());

        Game doublesGame = games.saved.stream().filter(g -> "DOUBLES".equals(g.getType())).findFirst().orElseThrow();
        List<DoublesPair> pairs = doublesPairs.saved.stream()
                .filter(p -> p.getGame().getId().equals(doublesGame.getId()))
                .toList();
        // jugadores lists the same name twice per side; only one row should result per side.
        assertEquals(2, pairs.size());
    }

    @Test
    void reRunningTheSameFixtureStoresNothingTwice() {
        BcnesaMatchReportContext context = firstFixture();

        run(context);
        run(context);

        assertEquals(1, matches.saved.size());
    }

    @Test
    void skipsAFixtureWhoseClubWasNeverImported() {
        BcnesaMatchReportContext context = firstFixture();

        new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games, doublesPairs)
                .process(context);

        assertTrue(matches.saved.isEmpty());
    }

    private void run(BcnesaMatchReportContext context) {
        processors.forEach(processor -> processor.process(context));
    }

    private static String homeName(Match match) {
        return match.getHomeTeam().getName();
    }

    private BcnesaMatchReportContext firstFixture() {
        return fixtureContext(0, "FALCONS DE SABADELL", "CTT DELS HORTS");
    }

    private BcnesaMatchReportContext secondFixture() {
        return fixtureContext(1, "CTT ATENEU", "AGRUPACIO CONGRES");
    }

    private BcnesaMatchReportContext doublesFixture() {
        Acta doublesActa = new ActaParser().parse(fixture("acta_doubles_matchday.json"));
        return new BcnesaMatchReportContext("2020-2021", "Preferent", "G1", "1a Fase", 7,0,
                "FALCONS DE SABADELL", "CTT DELS HORTS", fixture("acta_doubles_matchday.json"), doublesActa,
                doublesActa.games());
    }

    private BcnesaMatchReportContext fixtureContext(int fixtureIndex, String homeTeam, String awayTeam) {
        List<ActaGame> allGames = acta.games();
        int gamesPerFixture = allGames.size() / 2;
        List<ActaGame> fixtureGames = allGames.subList(fixtureIndex * gamesPerFixture,
                (fixtureIndex + 1) * gamesPerFixture);
        return new BcnesaMatchReportContext("2020-2021", "Preferent", "G1", "1a Fase", 7,fixtureIndex,
                homeTeam, awayTeam, fixture("acta_matchday.json"), acta, fixtureGames);
    }

    private static Path fixture(String name) {
        URL resource = BcnesaImportProcessorsTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
