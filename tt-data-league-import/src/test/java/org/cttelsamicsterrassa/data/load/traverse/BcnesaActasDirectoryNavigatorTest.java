package org.cttelsamicsterrassa.data.load.traverse;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcnesaActasDirectoryNavigatorTest {

    @TempDir
    Path baseFolder;

    private RecordingProcessor injected;

    @BeforeEach
    void setUp() {
        injected = new RecordingProcessor();
    }

    @Test
    void splitsAMatchdayIntoOneContextPerFixtureAndAttributesTheSecondFromTheClubIndex() throws IOException {
        // acta_2 exists purely so the club index has a header vote for licences 11/21/31/41; its own
        // fixture is dispatched too, giving three fixtures dispatched in total.
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                twoFixtureActa("HOME 1", "AWAY 1", "10", "20", "30", "40", "11", "21", "31", "41"));
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_2.json",
                singleFixtureActa("HOME 2", "AWAY 2", "11", "21", "31", "41"));

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(3, summary.fixturesSeen());
        assertEquals(3, summary.fixturesDispatched());
        assertEquals(0, summary.fixturesUnresolved());

        BcnesaMatchReportContext second = injected.contexts.stream()
                .filter(c -> "acta_1.json".equals(c.matchReportFile().getFileName().toString()))
                .filter(c -> c.fixtureIndex() == 1)
                .findFirst().orElseThrow();
        assertEquals("HOME 2", second.homeClubName());
        assertEquals("AWAY 2", second.awayClubName());
    }

    @Test
    void derivesSeasonGroupAndCompetitionFromThePathAndTheRoundFromThePayload() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta.json",
                singleFixtureActa(7, "HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));

        navigatorWith(injected).traverse(baseFolder);

        BcnesaMatchReportContext context = injected.single();
        assertEquals(Season.of(2020), context.toSeason());
        assertEquals(7, context.round());
        assertEquals(1, context.groupNumber());
        assertEquals("Preferent", context.competition());
        assertEquals("HOME CLUB", context.homeClubName());
        assertEquals("AWAY CLUB", context.awayClubName());
    }

    @Test
    void takesTheRoundFromThePayloadWhateverTheFileIsCalled() throws IOException {
        // The file name says day 1; the payload says day 9. The payload wins - the name is opaque.
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                singleFixtureActa(9, "HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));

        navigatorWith(injected).traverse(baseFolder);

        assertEquals(9, injected.single().round());
    }

    @Test
    void readsReportsUnderAnyOpaqueNameAndIgnoresOtherJsonFiles() throws IOException {
        Path folder = reportFolder("2020-2021", "Preferent", "G1", "1a Fase");
        Files.writeString(folder.resolve("acta.json"),
                singleFixtureActa("HOME 1", "AWAY 1", "10", "20", "30", "40"));
        Files.writeString(folder.resolve("acta_27236.json"),
                singleFixtureActa("HOME 2", "AWAY 2", "11", "21", "31", "41"));
        Files.writeString(folder.resolve("acta_match_0001.json"),
                singleFixtureActa("HOME 3", "AWAY 3", "12", "22", "32", "42"));
        // Not a match report: neither the walk nor the club index pre-pass may read it.
        Files.writeString(folder.resolve("clasificacion.json"), "{ not json");

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(3, summary.filesSeen());
        assertEquals(0, summary.filesSkipped());
        assertEquals(3, summary.fixturesDispatched());
    }

    @Test
    void skipsAReportWhosePayloadCarriesNoMatchDay() throws IOException {
        Path folder = reportFolder("2020-2021", "Preferent", "G1", "1a Fase");
        Files.writeString(folder.resolve("acta_no_day.json"),
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40")
                        .replace("\"jornada\": 1,", "\"jornada\": null,"));

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(1, summary.filesSeen());
        assertEquals(1, summary.filesSkipped());
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void skipsAFixtureWhoseClubsCannotBeAttributedAndKeepsGoing() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                twoFixtureActa("HOME 1", "AWAY 1", "10", "20", "30", "40", "99", "98", "97", "96"));

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(2, summary.fixturesSeen());
        assertEquals(1, summary.fixturesDispatched());
        assertEquals(1, summary.fixturesUnresolved());
        assertEquals("HOME 1", injected.single().homeClubName());
    }

    @Test
    void skipsFilesWhosePayloadCannotBeParsed() throws IOException {
        Path folder = reportFolder("2020-2021", "Preferent", "G1", "1a Fase");
        Files.writeString(folder.resolve("acta_1.json"), "{ not json");
        Files.writeString(folder.resolve("acta_2.json"),
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(2, summary.filesSeen());
        assertEquals(1, summary.filesSkipped());
        assertEquals(1, injected.contexts.size());
    }

    @Test
    void skipsFoldersThatDoNotFitTheExpectedLayout() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));
        writeReport("not-a-season", "Preferent", "G1", "1a Fase", "acta_2.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "11", "21", "31", "41"));
        writeReport("2020-2021", "Preferent", "not-a-group", "1a Fase", "acta_3.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "12", "22", "32", "42"));

        BcnesaTraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(1, injected.contexts.size());
    }

    @Test
    void oneFailingProcessorNeitherStopsTheRunNorBlocksItsPeers() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_2.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "11", "21", "31", "41"));
        BcnesaMatchReportProcessor failing = context -> {
            throw new IllegalStateException("boom");
        };
        RecordingProcessor peer = new RecordingProcessor();

        BcnesaTraversalSummary summary = navigatorWith(failing, peer).traverse(baseFolder);

        assertEquals(2, summary.fixturesDispatched());
        assertEquals(2, summary.processorFailures());
        assertEquals(2, peer.contexts.size());
    }

    @Test
    void anExplicitProcessorListOverridesTheInjectedOne() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));
        RecordingProcessor explicit = new RecordingProcessor();

        navigatorWith(injected).traverse(baseFolder, List.of(explicit));

        assertEquals(1, explicit.contexts.size());
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void traversesASingleSeasonWhenAskedTo() throws IOException {
        writeReport("2020-2021", "Preferent", "G1", "1a Fase", "acta_1.json",
                singleFixtureActa("HOME CLUB", "AWAY CLUB", "10", "20", "30", "40"));
        writeReport("2021-2022", "Preferent", "G1", "1a Fase", "acta_2.json",
                singleFixtureActa("HOME CLUB 2", "AWAY CLUB 2", "11", "21", "31", "41"));

        navigatorWith(injected).traverseSeason(baseFolder, "2021-2022");

        assertEquals(1, injected.contexts.size());
        assertEquals("HOME CLUB 2", injected.single().homeClubName());
    }

    @Test
    void rejectsABaseFolderThatIsNotADirectory() {
        Path missing = baseFolder.resolve("nope");

        assertThrows(IOException.class, () -> navigatorWith(injected).traverse(missing));
    }

    private BcnesaActasDirectoryNavigator navigatorWith(BcnesaMatchReportProcessor... processors) {
        return new BcnesaActasDirectoryNavigator(List.of(processors), new ActaParser());
    }

    private Path reportFolder(String season, String competition, String group, String phase) throws IOException {
        return Files.createDirectories(
                baseFolder.resolve(season).resolve(competition).resolve(group).resolve(phase));
    }

    private void writeReport(String season, String competition, String group, String phase, String fileName,
                             String content) throws IOException {
        Files.writeString(reportFolder(season, competition, group, phase).resolve(fileName), content);
    }

    /** A minimal single-fixture report: one A-vs-Y game, valid against the acta schema. */
    private static String singleFixtureActa(String home, String away, String aLicense, String bLicense,
                                            String xLicense, String yLicense) {
        return singleFixtureActa(1, home, away, aLicense, bLicense, xLicense, yLicense);
    }

    /** As {@link #singleFixtureActa}, with the match day the payload declares in {@code jornada}. */
    private static String singleFixtureActa(int jornada, String home, String away, String aLicense,
                                            String bLicense, String xLicense, String yLicense) {
        return acta(jornada, home, away, aLicense, bLicense, xLicense, yLicense, game(1, aLicense, yLicense));
    }

    /**
     * A two-fixture matchday: the first fixture is named by {@code equipos}/{@code alineaciones} as a
     * real BCNESA header would; the second reuses the same A-vs-Y crossover with different licences
     * and carries no header of its own, the way every fixture after the first does in the real export.
     */
    private static String twoFixtureActa(String home, String away, String a1, String b1, String x1, String y1,
                                         String a2, String b2, String x2, String y2) {
        return acta(1, home, away, a1, b1, x1, y1, game(1, a1, y1) + "," + game(2, a2, y2));
    }

    private static String game(int number, String homeLicense, String awayLicense) {
        return """
                {
                  "numero": %d,
                  "tipo": "individual",
                  "cruce": "A vs Y",
                  "local": { "letra": "A", "licencia": "%s", "nombre": "HOME A" },
                  "visitante": { "letra": "Y", "licencia": "%s", "nombre": "AWAY Y" },
                  "sets": [],
                  "resultado_juegos": { "local": 3, "visitante": 1 },
                  "ganador": "local",
                  "marcador_acumulado": { "local": 1, "visitante": 0 }
                }""".formatted(number, homeLicense, awayLicense);
    }

    private static String acta(int jornada, String home, String away, String aLicense, String bLicense,
                               String xLicense, String yLicense, String gamesJson) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "temporada": "2020/2021",
                  "competicion": "Preferent",
                  "grupo": 1,
                  "jornada": %d,
                  "fecha": "2020-10-01",
                  "hora": null,
                  "lugar": null,
                  "equipos": {
                    "local": { "id": null, "nombre": "%s", "delegado": null, "entrenador": null },
                    "visitante": { "id": null, "nombre": "%s", "delegado": null, "entrenador": null }
                  },
                  "abc_es_local": true,
                  "arbitros": { "principal": null, "asistente": null },
                  "alineaciones": {
                    "local": {
                      "A": { "licencia": "%s", "nombre": "HOME A" },
                      "B": { "licencia": "%s", "nombre": "HOME B" }
                    },
                    "visitante": {
                      "X": { "licencia": "%s", "nombre": "AWAY X" },
                      "Y": { "licencia": "%s", "nombre": "AWAY Y" }
                    }
                  },
                  "dobles": null,
                  "partidos": [%s],
                  "resultado_final": { "ganador": null, "marcador_partidos": { "local": 1, "visitante": 0 }, "marcador_juegos": null },
                  "acta_protestada": false
                }
                """.formatted(jornada, home, away, aLicense, bLicense, xLicense, yLicense, gamesJson);
    }

    private static final class RecordingProcessor implements BcnesaMatchReportProcessor {

        private final List<BcnesaMatchReportContext> contexts = new ArrayList<>();

        @Override
        public void process(BcnesaMatchReportContext context) {
            contexts.add(context);
        }

        private BcnesaMatchReportContext single() {
            assertEquals(1, contexts.size(), "expected exactly one dispatched fixture");
            return contexts.getFirst();
        }
    }
}
