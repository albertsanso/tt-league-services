package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttClubImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises FCTT's ordered processors using shared Acta fixtures with FCTT path context.
 */
class FcttImportProcessorsTest {

    private InMemoryRepositories.Clubs clubs;
    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.Players players;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private List<FcttMatchReportProcessor> processors;

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
                new FcttClubImportProcessor(teams),
                new FcttPlayerImportProcessor(players, playerSeasons),
                new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
    }

    @Test
    void storesClubsAndPlayersUnderTheFcttSource() {
        run(context("acta_singles.json", "G3"));

        assertEquals(2, clubs.byId.size());
        Club home = clubs.findClubBySourceAndName(ImportSource.FCTT, "HORTITEC ALZIRA TT").orElseThrow();
        assertEquals(ImportSource.FCTT, home.getSource());
        assertTrue(teams.findTeamByClubAndSeason(home.getId(), Season.of(2023)).isPresent());
        assertEquals(6, players.byId.size());
        assertTrue(playerSeasons.findPlayerSeasonByLicenseAndSeason(ImportSource.FCTT, "29194", Season.of(2023))
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
        assertEquals("Tercera nacional", match.getCompetition());
        assertEquals(3, match.getGroupNumber());
        assertEquals(1, match.getRound());
        assertEquals(6, lineups.saved.size());
        assertEquals(7, games.saved.size());
        assertEquals(30, setScores.saved.size());
        assertEquals(4, doublesPairs.saved.size());
        assertTrue(games.saved.stream().allMatch(game -> ImportSource.FCTT.equals(game.getSource())));
        Game doubles = games.saved.stream().filter(game -> "DOUBLES".equals(game.getType())).findFirst().orElseThrow();
        assertEquals("AWAY", doubles.getWinnerSide());
    }

    private void run(FcttMatchReportContext context) {
        processors.forEach(processor -> processor.process(context));
    }

    private static FcttMatchReportContext context(String fixture, String group) {
        Path file = fixture(fixture);
        Acta acta = new ActaParser().parse(file);
        return new FcttMatchReportContext("2023-2024", "Tercera nacional", group, acta.round(), file, acta);
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
