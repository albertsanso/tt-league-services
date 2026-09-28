package org.cttelsamicsterrassa.data.load.fctt.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Path reportFile = writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-42.json",
                report(9, "CLUB À", "CLUB B"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 1, 0, 0), summary);
        FcttMatchReportContext context = injected.single();
        assertEquals(Season.of(2023), context.toSeason());
        assertEquals("tercera-nacional-masculino", context.competition());
        assertEquals("G1", context.group());
        assertEquals(1, context.groupNumber().orElseThrow());
        assertEquals(9, context.round());
        assertEquals(reportFile, context.matchReportFile());
        assertEquals("CLUB À", context.acta().teams().home().name());
    }

    @Test
    void dispatchesMaleAndFemaleCompetitionsWithTheirGenderSuffix() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json",
                report(1, "HOME", "AWAY"));
        writeGrouplessReport("2023-2024", "female", "copa-catalana-femenina-1a", "jornada-1-partido-2.json",
                report(1, "HOME", "AWAY"));

        navigatorWith(injected).traverse(baseFolder);

        List<String> competitions = injected.contexts.stream().map(FcttMatchReportContext::competition).sorted().toList();
        assertEquals(List.of("copa-catalana-femenina-1a-femenino", "tercera-nacional-masculino"), competitions);
    }

    @Test
    void groupLessFemaleCompetitionDispatchesWithANullGroup() throws IOException {
        writeGrouplessReport("2026-2027", "female", "copa-catalana-femenina-1a", "jornada-1-partido-2.json",
                report(1, "HOME", "AWAY"));

        navigatorWith(injected).traverse(baseFolder);

        FcttMatchReportContext context = injected.single();
        assertNull(context.group());
        assertTrue(context.groupNumber().isEmpty());
        assertFalse(context.hasGroupFolder());
    }

    @Test
    void skipsAnUnknownGenderFolder() throws IOException {
        Path genderless = Files.createDirectories(baseFolder.resolve("2023-2024").resolve("mixed")
                .resolve("tercera-nacional").resolve("G1"));
        Files.writeString(genderless.resolve("jornada-1-partido-1.json"), report(1, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(0, summary.filesSeen());
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void readsMatchingOpaqueNamesInOrderAndIgnoresOtherFiles() throws IOException {
        Path folder = reportFolder("2023-2024", "male", "tercera-nacional", "G1");
        Files.writeString(folder.resolve("jornada-2-partido-2.json"), report(2, "HOME 2", "AWAY 2"));
        Files.writeString(folder.resolve("jornada-3-partido-1.json"), report(3, "HOME 3", "AWAY 3"));
        Files.writeString(folder.resolve("jornada_1_partido_1.json"), report(1, "OLD FORMAT", "AWAY"));
        Files.writeString(folder.resolve("acta.json"), "{ not json");
        Files.writeString(folder.resolve("jornada-1-partido-2.txt"), "{ not json");
        Files.writeString(folder.resolve("classification.json"), "{ not json");

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(2, summary.filesSeen());
        assertEquals(0, summary.skipped());
        assertEquals(List.of(2, 3), injected.contexts.stream().map(FcttMatchReportContext::round).toList());
    }

    @Test
    void matchesUnpublishedHomeAwayFileNames() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-78-86.json",
                report(1, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(1, summary.filesSeen());
        assertEquals(1, summary.dispatched());
    }

    @Test
    void skipsMalformedAndMissingRoundReportsAndContinues() throws IOException {
        Path folder = reportFolder("2023-2024", "male", "tercera-nacional", "G1");
        Files.writeString(folder.resolve("jornada-1-partido-1.json"), "{ not json");
        Files.writeString(folder.resolve("jornada-2-partido-2.json"), reportWithoutRound());
        Files.writeString(folder.resolve("jornada-3-partido-3.json"), report(3, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(3, 1, 2, 0), summary);
        assertEquals(3, injected.single().round());
    }

    @Test
    void skipsReportsInAGroupFolderWithoutAnExplicitNumber() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "playoffs", "jornada-1-partido-1.json",
                report(1, "HOME", "AWAY"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 0, 1, 0), summary);
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void skipsAReportWhosePayloadGenderContradictsTheFolder() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json",
                reportWithGender(1, "HOME", "AWAY", "femenino"));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 0, 1, 0), summary);
        assertTrue(injected.contexts.isEmpty());
    }

    @Test
    void skipsInvalidSeasonAndUnexpectedLayoutEntries() throws IOException {
        writeReport("not-a-season", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json", report(1, "HOME", "AWAY"));
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-3-partido-3.json", report(3, "HOME", "AWAY"));

        navigatorWith(injected).traverse(baseFolder);

        assertEquals(1, injected.contexts.size());
        assertEquals(3, injected.single().round());
    }

    @Test
    void isolatesProcessorFailuresWithoutBlockingPeers() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json", report(1, "HOME", "AWAY"));
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-2-partido-2.json", report(2, "HOME", "AWAY"));
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
    void skipsAPendingFixtureWithoutTeamsBeforeDispatch() throws IOException {
        writeGrouplessReport("2026-2027", "female", "copa-catalana-femenina-1a",
                "jornada-1-partido-1.json", reportWithoutTeams(1));

        TraversalSummary summary = navigatorWith(injected).traverse(baseFolder);

        assertEquals(new TraversalSummary(1, 0, 1, 0, List.of(),
                new ImportLifecycleCounters(0, 0, 0, 0, 0, 1)), summary);
        assertEquals(1, summary.lifecycle().unresolvedPendingFixtures(),
                "the no-team placeholder is recorded as unresolved");
        assertTrue(injected.contexts.isEmpty(), "no processor may create teams for a placeholder");
    }

    @Test
    void explicitProcessorsOverrideInjectedOnesAndSeasonFilteringWorks() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json", report(1, "HOME", "AWAY"));
        writeReport("2024-2025", "male", "tercera-nacional", "G1", "jornada-2-partido-2.json", report(2, "HOME", "AWAY"));
        RecordingProcessor explicit = new RecordingProcessor();

        navigatorWith(injected).traverseSeason(baseFolder, "2024-2025", List.of(explicit));

        assertTrue(injected.contexts.isEmpty());
        assertEquals(2, explicit.single().round());
    }

    @Test
    void reportsMonotonicDeterminateProgressWithARealTotalWhileTraversing() throws IOException {
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-1-partido-1.json", report(1, "HOME", "AWAY"));
        writeReport("2023-2024", "male", "tercera-nacional", "G1", "jornada-2-partido-2.json", report(2, "HOME", "AWAY"));
        List<org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress> updates = new ArrayList<>();

        navigatorWith(injected).traverse(baseFolder, List.of(injected),
                new org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext(
                        org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, null),
                updates::add);

        assertEquals(3, updates.size(), "an initial pre-count snapshot plus one update per file");
        assertEquals(0, updates.get(0).processed());
        assertEquals(1, updates.get(1).processed());
        assertEquals(2, updates.get(2).processed());
        updates.forEach(update -> assertEquals(2L, update.total().orElseThrow(),
                "the real total is known upfront and stays constant"));
        assertEquals(100.0, updates.get(2).percentage().orElseThrow());
    }

    @Test
    void rejectsABaseFolderThatIsNotADirectory() {
        assertThrows(IOException.class, () -> navigatorWith(injected).traverse(baseFolder.resolve("missing")));
    }

    private FcttActasDirectoryNavigator navigatorWith(FcttMatchReportProcessor... processors) {
        return new FcttActasDirectoryNavigator(List.of(processors), new ActaParser());
    }

    private Path reportFolder(String season, String gender, String competition, String group) throws IOException {
        return Files.createDirectories(baseFolder.resolve(season).resolve(gender).resolve(competition).resolve(group));
    }

    private Path competitionFolder(String season, String gender, String competition) throws IOException {
        return Files.createDirectories(baseFolder.resolve(season).resolve(gender).resolve(competition));
    }

    private Path writeReport(String season, String gender, String competition, String group, String fileName,
                             String content) throws IOException {
        Path report = reportFolder(season, gender, competition, group).resolve(fileName);
        Files.writeString(report, content);
        return report;
    }

    private Path writeGrouplessReport(String season, String gender, String competition, String fileName,
                                      String content) throws IOException {
        Path report = competitionFolder(season, gender, competition).resolve(fileName);
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

    private static String reportWithGender(int round, String home, String away, String gender) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": %d,
                  "genero": "%s",
                  "equipos": {
                    "local": { "id": null, "nombre": "%s" },
                    "visitante": { "id": null, "nombre": "%s" }
                  }
                }
                """.formatted(round, gender, home, away);
    }

    private static String reportWithoutRound() {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": null
                }
                """;
    }

    private static String reportWithoutTeams(int round) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": %d,
                  "acta_publicada": false,
                  "genero": "femenino",
                  "equipos": {
                    "local": { "id": null, "nombre": null },
                    "visitante": { "id": null, "nombre": null }
                  }
                }
                """.formatted(round);
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
