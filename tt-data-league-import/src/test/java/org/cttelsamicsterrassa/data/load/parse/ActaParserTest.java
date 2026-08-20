package org.cttelsamicsterrassa.data.load.parse;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses two real match reports copied verbatim from the RFETM export.
 */
class ActaParserTest {

    private final ActaParser parser = new ActaParser();

    @Test
    void parsesASinglesMatchReport() throws Exception {
        Acta acta = parser.parse(fixture("acta_singles.json"));

        assertEquals("2023/2024", acta.season());
        assertEquals(0, acta.group());
        assertEquals(1, acta.round());
        assertEquals(LocalDate.of(2023, 9, 29), acta.date());
        assertEquals(LocalTime.of(19, 0), acta.time());
        assertEquals("PABELLON PEREZ PUIG", acta.venue().venue());
        assertEquals("Alzira (Valencia)", acta.venue().city());
        assertFalse(acta.wasProtested());

        assertEquals("193", acta.teams().home().rfetmId());
        assertEquals("HORTITEC ALZIRA TT", acta.teams().home().name());
        assertEquals("23", acta.teams().away().rfetmId());

        // Letters A/B/C belong to the away side in this report.
        assertFalse(acta.abcIsHome());
        assertEquals(Set.of("X", "Y", "Z"), acta.lineups().home().keySet());
        assertEquals(Set.of("A", "B", "C"), acta.lineups().away().keySet());
        assertEquals("29194", acta.lineups().home().get("Y").license());
        assertEquals(1536.0, acta.lineups().home().get("Y").ranking(), 0.0001);

        assertNull(acta.doubles());
        assertEquals(6, acta.games().size());

        ActaGame first = acta.games().getFirst();
        assertEquals("A vs Y", first.crossover());
        assertFalse(first.isDoubles());
        assertFalse(first.wasNotPlayed());
        assertEquals("Y", first.home().letter());
        assertEquals("33851", first.away().license());
        assertEquals(3, first.sets().size());
        assertEquals(10, first.sets().getFirst().homePoints());
        assertEquals(12, first.sets().getFirst().awayPoints());
        assertEquals(ActaGame.WINNER_AWAY, first.winner());
        assertEquals(0, first.setsWon().home());
        assertEquals(3, first.setsWon().away());
        assertEquals(1, first.cumulativeScore().away());

        assertNull(acta.finalResult().winnerName());
        assertEquals(3, acta.finalResult().gamesWon().home());
        assertEquals(9, acta.finalResult().setsWon().away());
    }

    @Test
    void parsesADoublesMatchReport() throws Exception {
        Acta acta = parser.parse(fixture("acta_doubles.json"));

        assertNotNull(acta.doubles());
        assertEquals(2, acta.doubles().home().size());
        assertEquals(2, acta.doubles().away().size());
        assertEquals("CORDERO VELIZ, LUCíA", acta.doubles().home().getFirst().name());
        assertEquals("41528", acta.doubles().home().getFirst().license());

        ActaGame doubles = acta.games().stream()
                .filter(ActaGame::isDoubles)
                .findFirst()
                .orElseThrow();
        assertEquals(7, doubles.number());
        assertEquals("D vs D", doubles.crossover());
        assertEquals("Db", doubles.home().letter());
        assertNull(doubles.home().name());
        assertEquals(2, doubles.home().doublesPlayers().size());
        assertEquals("27924", doubles.home().doublesPlayers().get(1).license());
        assertEquals(5, doubles.sets().size());

        assertEquals("MUSEO DE LA ALMENDRA FRANCISCO MORALES", acta.finalResult().winnerName());
    }

    @Test
    void keepsPlayerNamesVerbatimIncludingUpstreamAccentDamage() throws Exception {
        Acta acta = parser.parse(fixture("acta_doubles.json"));

        // The source file spells this name with a lowercase accented letter mid-word. It is stored
        // exactly as written so the imported name matches the federation record.
        assertEquals("CORDERO VELIZ, LUCíA", acta.lineups().home().get("Y").name());
        assertTrue(acta.doubles().home().stream()
                .anyMatch(player -> "CORDERO VELIZ, LUCíA".equals(player.name())));
    }

    @Test
    void reportsTheFileWhenTheContentIsNotAValidActa(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("acta_1_2.json");
        Files.writeString(broken, "{ not json");

        ActaParseException failure = assertThrows(ActaParseException.class, () -> parser.parse(broken));

        assertEquals(broken, failure.getFile());
    }

    private static Path fixture(String name) throws URISyntaxException, IOException {
        URL resource = ActaParserTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        return Path.of(resource.toURI());
    }
}
