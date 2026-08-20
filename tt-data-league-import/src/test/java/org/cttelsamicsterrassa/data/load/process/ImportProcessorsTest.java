package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the three processors in their declared order over real match reports, the way the navigator
 * would.
 */
class ImportProcessorsTest {

    private InMemoryRepositories.Clubs clubs;
    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.Players players;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;

    private List<MatchReportProcessor> processors;

    @BeforeEach
    void setUp() {
        clubs = new InMemoryRepositories.Clubs();
        teams = new InMemoryRepositories.Teams();
        players = new InMemoryRepositories.Players();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        matches = new InMemoryRepositories.Matches();
        lineups = new InMemoryRepositories.Lineups();
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();

        processors = List.of(
                new RfetmClubImportProcessor(teams),
                new RfetmPlayerImportProcessor(players, playerSeasons),
                new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
    }

    @Test
    void storesClubsKeyedByTheirFederationId() {
        run(singlesContext());

        assertEquals(2, clubs.byId.size());
        Club home = clubs.findClubByName("HORTITEC ALZIRA TT").orElseThrow();
        assertEquals("HORTITEC ALZIRA TT", home.getName());
        assertEquals(2, teams.byId.size());
        assertTrue(teams.findTeamByClubAndSeason(home.getId(), Season.of(2023)).isPresent());
    }

    @Test
    void storesOnePlayerAndOneSeasonRegistrationPerLineupEntry() {
        run(singlesContext());

        assertEquals(6, players.byId.size());
        assertEquals(6, playerSeasons.byId.size());
        assertTrue(playerSeasons.findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, "29194", Season.of(2023)).isPresent());
    }

    @Test
    void storesTheMatchWithIdentityTakenFromThePath() {
        run(singlesContext());

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals("super-divisio-masculino", match.getCompetition());
        assertEquals(Season.of(2023), match.getSeason());
        assertEquals(0, match.getGroupNumber());
        assertEquals(1, match.getRound());
        assertEquals(LocalDate.of(2023, 9, 29), match.getDateTime().toLocalDate());
        assertEquals("PABELLON PEREZ PUIG", match.getVenue());
        assertEquals("JOSE VICENTE NICOLAS PELLICER", match.getRefereeName());
        assertEquals(3, match.getHomeGamesWon());
        assertEquals(9, match.getAwaySetsWon());
        // The report is a 3-3 draw, so no club won it.
        assertNull(match.getWinnerTeam());
    }

    @Test
    void storesLineupsWithTheLetterResolvedToAPosition() {
        run(singlesContext());

        assertEquals(6, lineups.saved.size());
        Lineup y = lineups.saved.stream().filter(l -> "Y".equals(l.getLetter())).findFirst().orElseThrow();
        assertEquals(2, y.getPosition());
        assertEquals("29194", y.getPlayer().getLicense());
        assertEquals(1536.0f, y.getRanking(), 0.0001f);
    }

    @Test
    void storesEachGameWithItsPlayersSetsAndWinningSide() {
        run(singlesContext());

        assertEquals(6, games.saved.size());
        Game first = games.saved.stream()
                .min(Comparator.comparingInt(Game::getGameNumber))
                .orElseThrow();
        assertEquals("INDIVIDUAL", first.getType());
        assertEquals("A vs Y", first.getCrossover());
        assertEquals("29194", first.getHomePlayer().getLicense());
        assertEquals("33851", first.getAwayPlayer().getLicense());
        assertEquals("AWAY", first.getWinnerSide());
        assertEquals("33851", first.getWinner().getLicense());
        assertEquals(0, first.getHomeSetsWon());
        assertEquals(3, first.getAwaySetsWon());

        assertEquals(18, setScores.saved.size());
        assertTrue(doublesPairs.saved.isEmpty());
    }

    @Test
    void resolvesDoublesPlayersAgainstTheLineupOfTheSameMatch() {
        run(doublesContext());

        Game doubles = games.saved.stream().filter(g -> "DOUBLES".equals(g.getType())).findFirst().orElseThrow();
        assertNull(doubles.getHomePlayer());
        assertNull(doubles.getAwayPlayer());
        // A doubles game has no winning player, only a winning side.
        assertNull(doubles.getWinner());
        assertEquals("AWAY", doubles.getWinnerSide());

        assertEquals(4, doublesPairs.saved.size());
        List<DoublesPair> home = doublesPairs.saved.stream().filter(p -> "HOME".equals(p.getSide())).toList();
        assertEquals(2, home.size());
        home.forEach(pair -> assertNotNull(pair.getPlayer().getLicense()));
    }

    @Test
    void storesDoublesPlayersMissingFromTheLineup() {
        Acta acta = doublesActaWithUnlistedHomePlayer();

        run(doublesContext(acta));

        assertTrue(playerSeasons.findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, "38106", Season.of(2023))
                .isPresent());
        assertTrue(doublesPairs.saved.stream().anyMatch(pair -> "38106".equals(pair.getPlayer().getLicense())));
    }

    @Test
    void reRunningTheSameReportStoresNothingTwice() {
        MatchReportContext context = singlesContext();

        run(context);
        run(context);

        assertEquals(2, clubs.byId.size());
        assertEquals(2, teams.byId.size());
        assertEquals(6, players.byId.size());
        assertEquals(6, playerSeasons.byId.size());
        assertEquals(1, matches.saved.size());
        assertEquals(6, lineups.saved.size());
        assertEquals(6, games.saved.size());
        assertEquals(18, setScores.saved.size());
    }

    @Test
    void skipsTheMatchWhenItsClubsWereNeverImported() {
        MatchReportContext context = singlesContext();

        new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                setScores, doublesPairs).process(context);

        assertTrue(matches.saved.isEmpty());
    }

    private void run(MatchReportContext context) {
        processors.forEach(processor -> processor.process(context));
    }

    private static MatchReportContext singlesContext() {
        return context("super-divisio", "1", "masculino", "193", "23", "acta_singles.json");
    }

    private static MatchReportContext doublesContext() {
        return doublesContext(new ActaParser().parse(fixture("acta_doubles.json")));
    }

    private static MatchReportContext doublesContext(Acta acta) {
        Path file = fixture("acta_doubles.json");
        return new MatchReportContext("2023-2024", "divisio-honor", "1", "femenino",
                RfetmClubKey.ofFederationId("16207", null), RfetmClubKey.ofFederationId("2017543", null), file, acta);
    }

    private static Acta doublesActaWithUnlistedHomePlayer() {
        Acta original = new ActaParser().parse(fixture("acta_doubles.json"));
        ActaLineupPlayer unlistedPlayer = new ActaLineupPlayer(null, "ALVAREZ IGLESIAS, MIGUEL", "38106", null);
        List<ActaGame> games = original.games().stream()
                .map(game -> game.isDoubles() ? gameWithUnlistedHomePlayer(game, unlistedPlayer) : game)
                .toList();
        return new Acta(original.federation(), original.season(), original.competition(), original.group(), original.round(),
                original.date(), original.time(), original.venue(), original.teams(), original.abcIsHome(), original.officials(),
                original.lineups(), original.doubles(), games, original.finalResult(), original.protested());
    }

    private static ActaGame gameWithUnlistedHomePlayer(ActaGame game, ActaLineupPlayer unlistedPlayer) {
        ActaParticipant home = game.home();
        ActaParticipant replacementHome = new ActaParticipant(home.letter(), home.name(), home.license(), home.rfetmId(),
                home.ranking(), List.of(unlistedPlayer, home.doublesPlayers().get(1)));
        return new ActaGame(game.number(), game.type(), game.crossover(), replacementHome, game.away(), game.sets(),
                game.setsWon(), game.winner(), game.cumulativeScore(), game.notPlayed(), game.reason());
    }

    private static MatchReportContext context(String competition,
                                              String day,
                                              String sex,
                                              String localTeamId,
                                              String visitorTeamId,
                                              String fixture) {
        Path file = fixture(fixture);
        Acta acta = new ActaParser().parse(file);
        return new MatchReportContext("2023-2024", competition, day, sex,
                RfetmClubKey.ofFederationId(localTeamId, null),
                RfetmClubKey.ofFederationId(visitorTeamId, null), file, acta);
    }

    private static Path fixture(String name) {
        URL resource = ImportProcessorsTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
