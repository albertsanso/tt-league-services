package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises FCTT's ordered processors using shared Acta fixtures with FCTT path context.
 */
class FcttImportProcessorsTest {

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private List<FcttMatchReportProcessor> processors;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);
        processors = List.of(
                new FcttTeamImportProcessor(teams),
                new FcttPlayerImportProcessor(playerSeasons),
                new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
    }

    @Test
    void storesTeamsAndSeasonRegistrationsUnderTheFcttSource() {
        run(context("acta_singles.json", "G3"));

        // The acta import registers Team and PlayerSeason rows under the FCTT source;
        // FederatedClub/Club and FederatedPlayer/Player rows belong to the consolidation
        // processors.
        assertEquals(2, teams.byId.size());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("HORTITEC ALZIRA TT", Season.of(2023),
                ImportSource.FCTT).isPresent());
        assertEquals(6, playerSeasons.byId.size());
        assertTrue(playerSeasons.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.FCTT, "29194", Season.of(2023))
                .isPresent());
    }

    @Test
    void storesCompleteMatchIncludingSetsAndDoublesAndIsIdempotent() {
        FcttMatchReportContext context = context("acta_doubles.json", "G3");

        run(context);
        run(context);

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(ImportSource.FCTT, match.getSource());
        assertEquals("Tercera nacional-masculino", match.getCompetition());
        assertEquals(3, match.getGroupNumber());
        assertEquals(1, match.getRound());
        assertEquals(MatchStatus.PLAYED, match.getStatus());
        assertEquals(6, lineups.saved.size());
        assertEquals(7, games.saved.size());
        assertEquals(30, setScores.saved.size());
        assertEquals(4, doublesPairs.saved.size());
        assertTrue(games.saved.stream().allMatch(game -> ImportSource.FCTT.equals(game.getSource())));
        Game doubles = games.saved.stream().filter(game -> "DOUBLES".equals(game.getType())).findFirst().orElseThrow();
        assertEquals("AWAY", doubles.getWinnerSide());
    }

    @Test
    void attachesLineupsGamesAndDoublesToTheRealSidesWhenTheAbcColumnWasTheAwayTeam() {
        // Real FCTT extract: A/B/C (CPP IGUALADA, away) written under "local"; the last running
        // score (3-4) mirrors the final score (4-3 for CTT ELS AMICS TERRASSA, home).
        run(context("acta_fctt_abc_away.json", "G2"));

        Match match = matches.saved.getFirst();
        assertEquals("CTT ELS AMICS TERRASSA", match.getHomeTeam().getName());
        assertEquals(4, match.getHomeGamesWon());
        assertEquals(3, match.getAwayGamesWon());
        assertEquals("CTT ELS AMICS TERRASSA", match.getWinnerTeam().getName());

        assertEquals(6, lineups.saved.size());
        lineups.saved.forEach(lineup -> assertEquals(
                "ABC".contains(lineup.getLetter()) ? "CPP IGUALADA" : "CTT ELS AMICS TERRASSA",
                lineup.getTeam().getName(), () -> "lineup " + lineup.getLetter()));

        List<Game> orderedGames = games.saved.stream()
                .sorted(java.util.Comparator.comparingInt(Game::getGameNumber)).toList();
        Game first = orderedGames.getFirst();
        assertEquals("PAGÈS MARIN, ANNA", first.getHomePlayer().getName());
        assertEquals("LUCO PEREZ, BERNAT", first.getAwayPlayer().getName());
        assertEquals("AWAY", first.getWinnerSide());
        assertEquals(1, first.getHomeSetsWon());
        assertEquals(3, first.getAwaySetsWon());
        assertEquals(List.of("AWAY", "HOME", "AWAY", "HOME", "AWAY", "HOME", "HOME"),
                orderedGames.stream().map(Game::getWinnerSide).toList());
        Game last = orderedGames.getLast();
        assertEquals(4, last.getCumulativeHomeSetsWon());
        assertEquals(3, last.getCumulativeAwaySetsWon());

        assertEquals(4, doublesPairs.saved.size());
        doublesPairs.saved.forEach(pair -> assertEquals(
                List.of("5405", "8311").contains(pair.getPlayer().getLicenseId()) ? "AWAY" : "HOME",
                pair.getSide(), () -> "doubles " + pair.getPlayer().getLicenseId()));
    }

    @Test
    void unpublishedActaStoresAScheduledMatchWithNoChildren() {
        run(context("acta_fctt_unpublished.json", "G1"));

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam());
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertEquals(0, lineups.saved.size());
        assertEquals(0, games.saved.size());
    }

    @Test
    void placeholderLicencesDoNotCollapseDifferentPlayersIntoOneRegistration() {
        // Real FCTT extract where every player carries licence "0". Resolving by that value
        // used to map all six players, and both doubles members, to one PlayerSeason.
        run(context("acta_fctt_placeholder_licences.json", "G3"));

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertTrue(playerSeasons.byId.isEmpty());
        assertEquals(0, lineups.saved.size());
        assertEquals(0, doublesPairs.saved.size());
        assertTrue(games.saved.stream().allMatch(game -> game.getHomePlayer() == null
                && game.getAwayPlayer() == null));
    }

    @Test
    void aDoublesPairListingTheSamePlayerTwiceStoresThatPlayerOnce() {
        // Real FCTT extract (copa-catalana-femenina-2a, partido 2926): the home pair names licence
        // 19214 twice, which used to insert two identical doubles_pair rows and break
        // uk_doubles_pair_game_side_player_source.
        FcttMatchReportContext context = context("acta_fctt_duplicate_doubles_player.json", "female",
                "copa-catalana-femenina-2a", "G2");

        run(context);
        run(context);

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertEquals(List.of("19214"), doublesPairs.saved.stream()
                .filter(pair -> "HOME".equals(pair.getSide()))
                .map(pair -> pair.getPlayer().getLicenseId())
                .toList());
        assertEquals(List.of("17807", "20277"), doublesPairs.saved.stream()
                .filter(pair -> "AWAY".equals(pair.getSide()))
                .map(pair -> pair.getPlayer().getLicenseId())
                .sorted()
                .toList());
    }

    @Test
    void phaseIsStoredAndIsPartOfTheNaturalKeySoReimportStaysIdempotent() {
        FcttMatchReportContext context = context("acta_doubles.json", "G3");

        run(context);
        run(context);

        assertEquals(1, matches.saved.size());
        assertEquals("1aFase", matches.saved.getFirst().getPhase());
    }

    @Test
    void groupLessFemaleReportStoresMatchWithNullGroupNumber() {
        run(context("acta_fctt_female_groupless.json", "female", "copa-catalana-femenina-1a", null));

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals("copa-catalana-femenina-1a-femenino", match.getCompetition());
        assertEquals(null, match.getGroupNumber());
    }

    private void run(FcttMatchReportContext context) {
        processors.forEach(processor -> processor.process(context));
    }

    private static FcttMatchReportContext context(String fixture, String group) {
        return context(fixture, "male", "Tercera nacional", group);
    }

    private static FcttMatchReportContext context(String fixture, String gender, String leagueCompetition, String group) {
        Path file = fixture(fixture);
        Acta acta = new ActaParser().parse(file);
        return new FcttMatchReportContext("2023-2024", gender, leagueCompetition, group, acta.round(), file, acta);
    }

    private static Path fixture(String name) {
        URL resource = FcttImportProcessorsTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
