package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
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
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the three BCNESA processors in their declared order over a real two-fixture matchday report,
 * the way the navigator would after splitting it.
 */
class BcnesaImportProcessorsTest {

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;

    private List<BcnesaMatchReportProcessor> processors;
    private Acta acta;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        setScores = new InMemoryRepositories.SetScores();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);

        processors = List.of(
                new BcnesaTeamImportProcessor(teams),
                new BcnesaPlayerImportProcessor(playerSeasons),
                new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));

        acta = new ActaParser().parse(fixture("acta_matchday.json"));
    }

    @Test
    void storesBothTeamsOfEachFixtureUnderTheBcnesaSource() {
        run(firstFixture());
        run(secondFixture());

        // The acta import registers Team rows only; FederatedClub and canonical Club rows are
        // created by the consolidation processors.
        assertEquals(4, teams.byId.size());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("FALCONS DE SABADELL", Season.of(2020),
                ImportSource.BCNESA).isPresent());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("CTT ATENEU", Season.of(2020),
                ImportSource.BCNESA).isPresent());
        assertEquals(4, teams.findAllTeamsBySource(ImportSource.BCNESA).size());
    }

    @Test
    void normalizesQuotedTeamLetterSuffixesToOneTeamRow() {
        BcnesaMatchReportContext quoted = fixtureContext(0, "CLUB ARIEL \"A\"", "CLUB ARIEL ''B''");
        BcnesaMatchReportContext bare = fixtureContext(1, "CLUB ARIEL A", "CLUB ARIEL B");

        run(quoted);
        run(bare);

        assertEquals(2, teams.byId.size());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("CLUB ARIEL A", Season.of(2020),
                ImportSource.BCNESA).isPresent());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("CLUB ARIEL B", Season.of(2020),
                ImportSource.BCNESA).isPresent());
    }

    @Test
    void storesOneSeasonRegistrationPerSinglesParticipantAcrossBothFixtures() {
        run(firstFixture());
        run(secondFixture());

        // 2 games per fixture, 2 distinct participants per game, no overlap between fixtures.
        // FederatedPlayer/Player rows are not created by the acta import; only the season
        // registration is.
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
    void storesThePhaseParsedFromTheReportContextOnEachImportedFixture() {
        run(firstFixture());

        Match first = matches.saved.stream()
                .filter(m -> "FALCONS DE SABADELL".equals(homeName(m)))
                .findFirst().orElseThrow();
        assertEquals("1a Fase", first.getPhase());
    }

    @Test
    void importsFixturesSharingARoundButBelongingToDifferentPhasesAsSeparateMatches() {
        BcnesaMatchReportContext firstPhase = firstFixture();
        BcnesaMatchReportContext secondPhase = fixtureContext(0, "FALCONS DE SABADELL", "CTT DELS HORTS", "2a Fase");

        run(firstPhase);
        run(secondPhase);

        assertEquals(2, matches.saved.size());
        assertTrue(matches.saved.stream().anyMatch(m -> "1a Fase".equals(m.getPhase())));
        assertTrue(matches.saved.stream().anyMatch(m -> "2a Fase".equals(m.getPhase())));
    }

    @Test
    void veteransOtherGroupFixtureIsPersistedWithANullGroupNumber() {
        run(veteransOtherGroupFixture());

        Match match = matches.saved.stream()
                .filter(m -> "FALCONS DE SABADELL".equals(homeName(m)))
                .findFirst().orElseThrow();
        assertNull(match.getGroupNumber());
        assertEquals("Play Off", match.getPhase());
    }

    @Test
    void reRunningTheSameVeteransOtherGroupFixtureStoresNothingTwiceDespiteTheNullGroup() {
        BcnesaMatchReportContext context = veteransOtherGroupFixture();

        run(context);
        run(context);

        assertEquals(1, matches.saved.size());
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

        new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games, setScores, doublesPairs)
                .process(context);

        assertTrue(matches.saved.isEmpty());
    }

    @Test
    void storesLineupsGamesAndScoreOnTheRealSidesWhenTheXyzTeamIsHome() {
        // Real RTBTT page: header lists CTT DELS HORTS 2000 (home) first, while its players sit in
        // the XYZ column; the extractor writes it by real side with abc_es_local false.
        Acta xyzHome = new ActaParser().parse(fixture("acta_bcnesa_xyz_home.json"));
        run(new BcnesaMatchReportContext("2020-2021", "Preferent", "G1", "1a Fase", 10, 0,
                xyzHome.teams().home().name(), xyzHome.teams().away().name(),
                fixture("acta_bcnesa_xyz_home.json"), xyzHome, xyzHome.games()));

        Match match = matches.saved.getFirst();
        assertEquals("CTT DELS HORTS 2000", homeName(match));
        assertEquals("CTT RIPOLLET", match.getAwayTeam().getName());
        assertEquals(MatchStatus.PLAYED, match.getStatus());
        assertEquals(4, match.getHomeGamesWon());
        assertEquals(2, match.getAwayGamesWon());
        assertEquals(match.getHomeTeam(), match.getWinnerTeam());

        assertEquals(6, lineups.saved.size());
        lineups.saved.forEach(lineup -> assertEquals(
                "XYZ".contains(lineup.getLetter()) ? "CTT DELS HORTS 2000" : "CTT RIPOLLET",
                lineup.getTeam().getName(), () -> "lineup " + lineup.getLetter()));

        Game first = games.saved.stream().filter(g -> g.getGameNumber() == 1).findFirst().orElseThrow();
        assertEquals("4016", first.getHomePlayer().getLicenseId());
        assertEquals("8089", first.getAwayPlayer().getLicenseId());
        assertEquals("HOME", first.getWinnerSide());
        assertEquals(3, first.getHomeSetsWon());
        assertEquals(0, first.getAwaySetsWon());
    }

    @Test
    void storesTheSetScoresOfEveryGameFromTheHtmlBasedActas() {
        // Real 2026-2027 acta: the HTML-based export carries the points of every set, doubles included.
        Acta published = new ActaParser().parse(fixture("acta_bcnesa_2026_published.json"));
        run(new BcnesaMatchReportContext("2026-2027", "RTB 1a COMARCAL", "G2", "1a Fase", 1, 0,
                published.teams().home().name(), published.teams().away().name(),
                fixture("acta_bcnesa_2026_published.json"), published, published.games()));

        assertEquals(7, games.saved.size());
        assertEquals(28, setScores.saved.size());
        setScores.saved.forEach(setScore -> assertEquals(ImportSource.BCNESA, setScore.getSource()));

        Game first = games.saved.stream().filter(g -> g.getGameNumber() == 1).findFirst().orElseThrow();
        List<SetScore> firstSets = setScores.saved.stream()
                .filter(setScore -> setScore.getGame() == first)
                .sorted(Comparator.comparingInt(SetScore::getSetNumber))
                .toList();
        assertEquals(4, firstSets.size());
        assertEquals(13, firstSets.get(1).getHomePoints());
        assertEquals(15, firstSets.get(1).getAwayPoints());

        Game doubles = games.saved.stream().filter(g -> g.getGameNumber() == 7).findFirst().orElseThrow();
        assertEquals(5, setScores.saved.stream().filter(setScore -> setScore.getGame() == doubles).count());
    }

    @Test
    void storesNoSetScoresForThePdfBasedActasThatHaveNone() {
        run(firstFixture());

        assertFalse(games.saved.isEmpty());
        assertTrue(setScores.saved.isEmpty());
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
        return fixtureContext(fixtureIndex, homeTeam, awayTeam, "1a Fase");
    }

    private BcnesaMatchReportContext fixtureContext(int fixtureIndex, String homeTeam, String awayTeam, String phase) {
        return fixtureContext(fixtureIndex, homeTeam, awayTeam, "Preferent", "G1", phase);
    }

    private BcnesaMatchReportContext veteransOtherGroupFixture() {
        return fixtureContext(0, "FALCONS DE SABADELL", "CTT DELS HORTS", "Veterans", "Other", "Play Off");
    }

    private BcnesaMatchReportContext fixtureContext(int fixtureIndex, String homeTeam, String awayTeam,
                                                     String competition, String group, String phase) {
        List<ActaGame> allGames = acta.games();
        int gamesPerFixture = allGames.size() / 2;
        List<ActaGame> fixtureGames = allGames.subList(fixtureIndex * gamesPerFixture,
                (fixtureIndex + 1) * gamesPerFixture);
        return new BcnesaMatchReportContext("2020-2021", competition, group, phase, 7,fixtureIndex,
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
