package org.cttelsamicsterrassa.data.load.traverse;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfetmActasDirectoryNavigatorTest {

    @TempDir
    Path baseFolder;

    private RecordingProcessor injected;

    @BeforeEach
    void setUp() {
        injected = new RecordingProcessor();
    }

    @Test
    void buildsAContextPerMatchReportFromThePathAndThePayload() throws IOException {
        writeReport("2023-2024", "super-divisio", "3", "masculino", "acta.json", acta("193", "23"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        MatchReportContext context = injected.single();
        assertEquals("2023-2024", context.season());
        assertEquals("super-divisio", context.leagueCompetition());
        assertEquals("3", context.day());
        assertEquals("masculino", context.sex());
        assertEquals("193", context.homeTeam().value());
        assertEquals("23", context.awayTeam().value());
        assertTrue(context.homeTeam().isFederationId());
        assertNotNull(context.acta());
        assertEquals("HORTITEC ALZIRA TT", context.acta().teams().home().name());
    }

    @Test
    void derivesSeasonRoundAndCompetitionFromThePath() throws IOException {
        writeReport("2023-2024", "divisio-honor", "12", "femenino", "acta.json", acta("1", "2"));

        navigatorWith(injected).traverse(baseFolder);

        MatchReportContext context = injected.single();
        // Folders use 2023-2024 while the payload uses 2023/2024; normalisation happens here only.
        assertEquals(Season.of(2023), context.toSeason());
        assertEquals(12, context.round());
        assertEquals("divisio-honor-femenino", context.competition());
    }

    @Test
    void takesTheTeamIdsFromThePayloadWhateverTheFileIsCalled() throws IOException {
        // The export names reports several ways; none of them is parsed for ids.
        Path folder = reportFolder("2023-2024", "super-divisio", "1", "masculino");
        Files.writeString(folder.resolve("acta.json"), acta("10", "20"));
        Files.writeString(folder.resolve("acta_27236.json"), acta("30", "40"));
        Files.writeString(folder.resolve("acta_match_0001.json"), acta("50", "60"));
        Files.writeString(folder.resolve("acta_70_80.json"), acta("70", "80"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(4, 4, 0, 0), summary);
        assertEquals(List.of("10", "30", "50", "70"),
                injected.contexts.stream().map(c -> c.homeTeam().value()).sorted().toList());
    }

    @Test
    void identifiesTeamsByNameWhenThePayloadCarriesNoIds() throws IOException {
        // The whole 2025-2026 season is exported without team ids; it must still import.
        writeReport("2025-2026", "super-divisio", "0", "masculino", "acta_27248.json", acta(null, null));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        MatchReportContext context = injected.single();
        assertFalse(context.homeTeam().isFederationId());
        assertTrue(context.homeTeam().value().startsWith("nm:"));
        assertEquals("HORTITEC ALZIRA TT", context.homeTeam().name());
        assertNotEquals(context.homeTeam().value(), context.awayTeam().value());
    }

    @Test
    void usesTheIdForOneSideAndTheNameForTheOtherWhenOnlyOneIdIsPresent() throws IOException {
        writeReport("2025-2026", "super-divisio", "0", "masculino", "acta_1.json", acta("5", null));

        navigatorWith(injected).traverse(baseFolder);

        MatchReportContext context = injected.single();
        assertEquals("5", context.homeTeam().value());
        assertFalse(context.awayTeam().isFederationId());
    }

    @Test
    void givesOneTeamTheSameKeyAcrossReportsButKeepsCompetitionsApart() throws IOException {
        // Same club name, same season: one key within a competition, a different one across
        // competitions - that is what keeps a club's A and B teams from being merged.
        writeReport("2025-2026", "super-divisio", "1", "masculino", "acta_1.json",
                acta(null, null, "SHARED NAME", "OTHER A"));
        writeReport("2025-2026", "super-divisio", "2", "masculino", "acta_2.json",
                acta(null, null, "SHARED NAME", "OTHER B"));
        writeReport("2025-2026", "primera-divisio", "1", "masculino", "acta_3.json",
                acta(null, null, "SHARED NAME", "OTHER C"));

        navigatorWith(injected).traverse(baseFolder);

        List<String> superDivisio = injected.contexts.stream()
                .filter(c -> "super-divisio".equals(c.leagueCompetition()))
                .map(c -> c.homeTeam().value()).distinct().toList();
        List<String> primera = injected.contexts.stream()
                .filter(c -> "primera-divisio".equals(c.leagueCompetition()))
                .map(c -> c.homeTeam().value()).distinct().toList();

        assertEquals(1, superDivisio.size(), "one key for the team across its own competition");
        assertNotEquals(superDivisio.getFirst(), primera.getFirst(), "different competition, different team");
    }

    @Test
    void skipsAReportThatIdentifiesASideByNeitherIdNorNameAndKeepsGoing() throws IOException {
        Path folder = reportFolder("2025-2026", "super-divisio", "0", "masculino");
        Files.writeString(folder.resolve("acta_27236.json"), acta(null, null, null, null));
        Files.writeString(folder.resolve("acta_10_20.json"), acta("10", "20"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(2, 1, 1, 0), summary);
        assertEquals("10", injected.single().homeTeam().value());
    }

    @Test
    void skipsReportsWhosePayloadCannotBeParsed() throws IOException {
        Path folder = reportFolder("2023-2024", "super-divisio", "1", "masculino");
        Files.writeString(folder.resolve("acta_1.json"), "{ not json");
        Files.writeString(folder.resolve("acta_2.json"), acta("3", "4"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(2, 1, 1, 0), summary);
        assertEquals("3", injected.single().homeTeam().value());
    }

    @Test
    void skipsFoldersThatDoNotFitTheExpectedLayout() throws IOException {
        writeReport("2023-2024", "super-divisio", "1", "masculino", "acta.json", acta("1", "2"));
        writeReport("not-a-season", "super-divisio", "1", "masculino", "acta.json", acta("3", "4"));
        writeReport("2023-2024", "super-divisio", "final", "masculino", "acta.json", acta("5", "6"));
        writeReport("2023-2024", "super-divisio", "2", "mixto", "acta.json", acta("7", "8"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        assertEquals("1", injected.single().homeTeam().value());
    }

    @Test
    void oneFailingProcessorNeitherStopsTheRunNorBlocksItsPeers() throws IOException {
        writeReport("2023-2024", "super-divisio", "1", "masculino", "acta.json", acta("1", "2"));
        writeReport("2023-2024", "super-divisio", "2", "masculino", "acta.json", acta("3", "4"));
        MatchReportProcessor failing = context -> {
            throw new IllegalStateException("boom");
        };
        RecordingProcessor peer = new RecordingProcessor();

        TraversalSummary summary = navigatorWith(failing, peer).traverse(baseFolder);

        assertEquals(new TraversalSummary(2, 2, 0, 2), summary);
        assertEquals(2, peer.contexts.size());
    }

    @Test
    void anExplicitProcessorListOverridesTheInjectedOne() throws IOException {
        writeReport("2023-2024", "super-divisio", "1", "masculino", "acta.json", acta("1", "2"));
        RecordingProcessor explicit = new RecordingProcessor();

        navigatorWith(injected).traverse(baseFolder, List.of(explicit));

        assertEquals(1, explicit.contexts.size());
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void traversesASingleSeasonWhenAskedTo() throws IOException {
        writeReport("2023-2024", "super-divisio", "1", "masculino", "acta.json", acta("1", "2"));
        writeReport("2024-2025", "super-divisio", "1", "masculino", "acta.json", acta("3", "4"));

        TraversalSummary summary = navigatorWith(injected).traverseSeason(baseFolder, "2024-2025");

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        assertEquals("3", injected.single().homeTeam().value());
    }

    @Test
    void rejectsABaseFolderThatIsNotADirectory() {
        Path missing = baseFolder.resolve("nope");

        assertThrows(IOException.class, () -> navigatorWith(injected).traverse(missing));
    }

    private RfetmActasDirectoryNavigator navigatorWith(MatchReportProcessor... processors) {
        return new RfetmActasDirectoryNavigator(List.of(processors), new ActaParser());
    }

    private Path reportFolder(String season, String competition, String day, String sex) throws IOException {
        return Files.createDirectories(baseFolder.resolve(season).resolve(competition).resolve(day).resolve(sex));
    }

    private void writeReport(String season, String competition, String day, String sex, String fileName,
                             String content) throws IOException {
        Files.writeString(reportFolder(season, competition, day, sex).resolve(fileName), content);
    }

    /**
     * A minimal report that is valid against the acta schema. A {@code null} team id is written as
     * JSON {@code null}, reproducing the reports in the export that carry no ids at all.
     */
    private static String acta(String localTeamId, String visitorTeamId) {
        return acta(localTeamId, visitorTeamId, "HORTITEC ALZIRA TT", "C.E.R. L´ESCALA");
    }

    /**
     * As {@link #acta(String, String)}, with the team names too. A {@code null} name is written as
     * JSON {@code null}, reproducing a side that can be identified by nothing at all.
     */
    private static String acta(String localTeamId, String visitorTeamId,
                               String localName, String visitorName) {
        return """
                {
                  "federacion": "Real Federación Española de Tenis de Mesa",
                  "temporada": "2023/2024",
                  "competicion": "Temporada 2023-2024",
                  "grupo": 0,
                  "jornada": 1,
                  "fecha": "2023-09-29",
                  "hora": "19:00",
                  "lugar": { "recinto": "PABELLON PEREZ PUIG", "ciudad": "Alzira (Valencia)" },
                  "equipos": {
                    "local": { "nombre": %s, "id": %s, "delegado": null, "entrenador": null },
                    "visitante": { "nombre": %s, "id": %s, "delegado": null, "entrenador": null }
                  },
                  "abc_es_local": false,
                  "arbitros": { "principal": null, "asistente": null },
                  "alineaciones": {
                    "local": { "X": { "nombre": "A, A", "licencia": "1", "id": "1" } },
                    "visitante": { "A": { "nombre": "B, B", "licencia": "2", "id": "2" } }
                  },
                  "dobles": null,
                  "partidos": [],
                  "resultado_final": {
                    "ganador": null,
                    "marcador_partidos": { "local": 0, "visitante": 0 },
                    "marcador_juegos": { "local": 0, "visitante": 0 }
                  },
                  "acta_protestada": false
                }
                """.formatted(jsonString(localName), jsonString(localTeamId),
                jsonString(visitorName), jsonString(visitorTeamId));
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static final class RecordingProcessor implements MatchReportProcessor {

        private final List<MatchReportContext> contexts = new ArrayList<>();

        @Override
        public void process(MatchReportContext context) {
            contexts.add(context);
        }

        private MatchReportContext single() {
            assertEquals(1, contexts.size(), "expected exactly one dispatched report");
            return contexts.getFirst();
        }
    }
}
