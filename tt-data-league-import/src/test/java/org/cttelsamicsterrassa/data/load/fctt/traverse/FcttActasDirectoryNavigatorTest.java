package org.cttelsamicsterrassa.data.load.fctt.traverse;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FcttActasDirectoryNavigatorTest {

    @TempDir
    Path baseFolder;

    private RecordingProcessor injected;

    @BeforeEach
    void setUp() {
        injected = new RecordingProcessor();
    }

    @Test
    void derivesContextFromPathAndPayloadRatherThanOpaqueFilename() throws IOException {
        Path reportFile = writeReport("2023-2024", "Tercera nacional", "G1", "jornada_1_partido_42.json",
                report(9, "CLUB À", "CLUB B"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        FcttMatchReportContext context = injected.single();
        assertEquals(Season.of(2023), context.toSeason());
        assertEquals("Tercera nacional", context.competition());
        assertEquals("G1", context.group());
        assertEquals(1, context.groupNumber().orElseThrow());
        assertEquals(9, context.round());
        assertEquals(reportFile, context.matchReportFile());
        assertEquals("CLUB À", context.acta().teams().home().name());
    }

    @Test
    void readsMatchingOpaqueNamesInOrderAndIgnoresOtherFiles() throws IOException {
        Path folder = reportFolder("2023-2024", "Tercera nacional", "G1");
        Files.writeString(folder.resolve("jornada_alpha_partido_beta.json"), report(2, "HOME 2", "AWAY 2"));
        Files.writeString(folder.resolve("jornada_z_partido_1.json"), report(3, "HOME 3", "AWAY 3"));
        Files.writeString(folder.resolve("acta.json"), "{ not json");
        Files.writeString(folder.resolve("jornada_1_partido_2.txt"), "{ not json");
        Files.writeString(folder.resolve("classification.json"), "{ not json");

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(2, summary.filesSeen());
        assertEquals(0, summary.skipped());
        assertEquals(List.of(2, 3), injected.contexts.stream().map(FcttMatchReportContext::round).toList());
    }

    @Test
    void skipsMalformedAndMissingRoundReportsAndContinues() throws IOException {
        Path folder = reportFolder("2023-2024", "Tercera nacional", "G1");
        Files.writeString(folder.resolve("jornada_1_partido_1.json"), "{ not json");
        Files.writeString(folder.resolve("jornada_2_partido_2.json"), reportWithoutRound());
        Files.writeString(folder.resolve("jornada_3_partido_3.json"), report(3, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(3, 1, 2, 0), summary);
        assertEquals(3, injected.single().round());
    }

    @Test
    void skipsReportsInAGroupFolderWithoutAnExplicitNumber() throws IOException {
        writeReport("2023-2024", "Tercera nacional", "playoffs", "jornada_1_partido_1.json",
                report(1, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 0, 1, 0), summary);
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void skipsInvalidSeasonAndUnexpectedLayoutEntries() throws IOException {
        writeReport("not-a-season", "Tercera nacional", "G1", "jornada_1_partido_1.json", report(1, "HOME", "AWAY"));
        Path competition = Files.createDirectories(baseFolder.resolve("2023-2024").resolve("Tercera nacional"));
        Files.writeString(competition.resolve("jornada_2_partido_2.json"), report(2, "HOME", "AWAY"));
        writeReport("2023-2024", "Tercera nacional", "G1", "jornada_3_partido_3.json", report(3, "HOME", "AWAY"));

        navigatorWith(injected).traverse(baseFolder);

        assertEquals(1, injected.contexts.size());
        assertEquals(3, injected.single().round());
    }

    @Test
    void isolatesProcessorFailuresWithoutBlockingPeers() throws IOException {
        writeReport("2023-2024", "Tercera nacional", "G1", "jornada_1_partido_1.json", report(1, "HOME", "AWAY"));
        writeReport("2023-2024", "Tercera nacional", "G1", "jornada_2_partido_2.json", report(2, "HOME", "AWAY"));
        FcttMatchReportProcessor failing = context -> {
            throw new IllegalStateException("boom");
        };
        RecordingProcessor peer = new RecordingProcessor();

        TraversalSummary summary = navigatorWith(failing, peer).traverse(baseFolder);

        assertEquals(2, summary.dispatched());
        assertEquals(2, summary.processorFailures());
        assertEquals(2, peer.contexts.size());
    }

    @Test
    void explicitProcessorsOverrideInjectedOnesAndSeasonFilteringWorks() throws IOException {
        writeReport("2023-2024", "Tercera nacional", "G1", "jornada_1_partido_1.json", report(1, "HOME", "AWAY"));
        writeReport("2024-2025", "Tercera nacional", "G1", "jornada_2_partido_2.json", report(2, "HOME", "AWAY"));
        RecordingProcessor explicit = new RecordingProcessor();

        navigatorWith(injected).traverseSeason(baseFolder, "2024-2025", List.of(explicit));

        assertTrue(injected.contexts.isEmpty());
        assertEquals(2, explicit.single().round());
    }

    @Test
    void rejectsABaseFolderThatIsNotADirectory() {
        assertThrows(IOException.class, () -> navigatorWith(injected).traverse(baseFolder.resolve("missing")));
    }

    private FcttActasDirectoryNavigator navigatorWith(FcttMatchReportProcessor... processors) {
        return new FcttActasDirectoryNavigator(List.of(processors), new ActaParser());
    }

    private Path reportFolder(String season, String competition, String group) throws IOException {
        return Files.createDirectories(baseFolder.resolve(season).resolve(competition).resolve(group));
    }

    private Path writeReport(String season, String competition, String group, String fileName, String content)
            throws IOException {
        Path report = reportFolder(season, competition, group).resolve(fileName);
        Files.writeString(report, content);
        return report;
    }

    private static String report(int round, String home, String away) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": %d,
                  "equipos": {
                    "local": { "id": null, "nombre": "%s" },
                    "visitante": { "id": null, "nombre": "%s" }
                  }
                }
                """.formatted(round, home, away);
    }

    private static String reportWithoutRound() {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": null
                }
                """;
    }

    private static final class RecordingProcessor implements FcttMatchReportProcessor {

        private final List<FcttMatchReportContext> contexts = new ArrayList<>();

        @Override
        public void process(FcttMatchReportContext context) {
            contexts.add(context);
        }

        private FcttMatchReportContext single() {
            assertEquals(1, contexts.size(), "expected exactly one dispatched report");
            return contexts.getFirst();
        }
    }
}
