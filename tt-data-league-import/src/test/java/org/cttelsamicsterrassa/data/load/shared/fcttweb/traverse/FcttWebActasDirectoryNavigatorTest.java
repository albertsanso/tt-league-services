package org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaMatchdaySplitter;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
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

/** The shared reader of the {@code tt-league-ingest} layout, driven directly for each source. */
class FcttWebActasDirectoryNavigatorTest {

    private static final FcttWebContextFactory<BcnesaMatchReportContext> BCNESA = report ->
            new BcnesaMatchReportContext(report.season(), report.competition(), report.group(), report.phase(),
                    report.round(), report.fixtureIndex(), report.homeTeamName(), report.awayTeamName(),
                    report.file(), report.acta(), report.games(), report.runContext());
    private static final FcttWebContextFactory<FcttMatchReportContext> FCTT = report ->
            new FcttMatchReportContext(report.season(), report.gender(), report.competition(), report.group(),
                    report.round(), report.file(), report.acta(), report.runContext());

    @TempDir
    Path baseFolder;

    @Test
    void bcnesaMapsRtbFoldersToTheLegacyCompetitionNameAndKeepsGroupAndPhaseFolders() throws IOException {
        Path report = write("2026-2027/rtb-1a-comarcal/G2/1a Fase/jornada_07_local_team_11_away_team_12.json",
                payload(7, "CLUB A", "CLUB B", null));
        FcttWebActasDirectoryNavigator navigator = navigator(ImportSource.BCNESA);
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        List<BcnesaMatchReportContext> contexts = new ArrayList<>();

        walk(navigator, "2026-2027", counters, BCNESA, contexts::add);

        BcnesaMatchReportContext context = contexts.getFirst();
        assertEquals(1, contexts.size());
        assertEquals("1a Comarcal", context.competition());
        assertEquals("G2", context.group());
        assertEquals(2, context.groupNumber());
        assertEquals("1a Fase", context.phase());
        assertEquals(7, context.round());
        assertEquals(0, context.fixtureIndex());
        assertEquals("CLUB A", context.homeTeamName());
        assertEquals("CLUB B", context.awayTeamName());
        assertEquals(report, context.matchReportFile());
        assertEquals(1, counters.filesSeen());
        assertEquals(1, counters.fixturesSeen());
        assertEquals(0, counters.filesSkipped());
    }

    @Test
    void bcnesaLeavesLegacyNamesUnmappedFoldersAndForeignGroupsToTheLegacyNavigator() throws IOException {
        write("2026-2027/Preferent/G1/1a Fase/acta_3_page_1.json", payload(3, "A", "B", null));
        write("2026-2027/rtb-unknown/G1/1a Fase/jornada_01_local_team_1_away_team_2.json", payload(1, "A", "B", null));
        write("2026-2027/rtb-preferent/Final/1a Fase/jornada_01_local_team_1_away_team_2.json",
                payload(1, "A", "B", null));
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        List<BcnesaMatchReportContext> contexts = new ArrayList<>();

        walk(navigator(ImportSource.BCNESA), "2026-2027", counters, BCNESA, contexts::add);

        assertTrue(contexts.isEmpty());
        assertEquals(0, counters.filesSeen(), "files the shared navigator does not read are not its to count");
    }

    @Test
    void bcnesaReportsAnUnattributedFixtureInsteadOfGuessingItsClubs() throws IOException {
        write("2026-2027/rtb-preferent/G1/1a Fase/jornada_01_local_team_1_away_team_2.json",
                """
                {"federacion": "x", "jornada": 1, "acta_publicada": false}
                """);
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        List<BcnesaMatchReportContext> contexts = new ArrayList<>();

        navigator(ImportSource.BCNESA).traverseSeason(baseFolder.resolve("2026-2027"), "2026-2027", runContext,
                counters, org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener.noop(),
                BCNESA, contexts::add);

        assertTrue(contexts.isEmpty());
        assertEquals(1, counters.fixturesSeen());
        assertEquals(1, counters.fixturesUnresolved());
        assertEquals(1, runContext.lifecycleCounters().unresolvedPendingFixtures());
    }

    @Test
    void bcnesaTakesTheRoundOfAVeteransOtherFileFromItsNameOnlyWhenThePayloadHasNone() throws IOException {
        write("2026-2027/rtb-veterans-1a/Other/Play Off/jornada_04_local_team_1_away_team_2.json",
                payload(null, "A", "B", null));
        write("2026-2027/rtb-preferent/G1/1a Fase/jornada_05_local_team_1_away_team_2.json",
                payload(null, "A", "B", null));
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        List<BcnesaMatchReportContext> contexts = new ArrayList<>();

        navigator(ImportSource.BCNESA).traverseSeason(baseFolder.resolve("2026-2027"), "2026-2027", runContext,
                counters, org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener.noop(),
                BCNESA, contexts::add);

        assertEquals(1, contexts.size());
        BcnesaMatchReportContext veterans = contexts.getFirst();
        assertEquals(4, veterans.round());
        assertEquals("Vet 1a", veterans.competition());
        assertNull(veterans.groupNumber());
        assertEquals(1, counters.filesSkipped(), "a numbered group without a payload round is skipped");
    }

    @Test
    void fcttStoresTdmAsTerceraNacionalAndTakesTheGenderFromThePayload() throws IOException {
        write("2026-2027/tdm/g1/regular/jornada_10_local_team_130_away_team_151.json",
                payload(10, "HOME", "AWAY", "masculino"));
        write("2026-2027/copa-catalana-femenina/other/regular/jornada_1_local_team_1_away_team_2.json",
                payload(1, "HOME", "AWAY", "femenino"));
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        List<FcttMatchReportContext> contexts = new ArrayList<>();

        walk(navigator(ImportSource.FCTT), "2026-2027", counters, FCTT, contexts::add);

        assertEquals(List.of("copa-catalana-femenina-femenino", "tercera-nacional-masculino"),
                contexts.stream().map(FcttMatchReportContext::competition).sorted().toList());
        FcttMatchReportContext tdm = contexts.stream()
                .filter(context -> context.competition().startsWith("tercera")).findFirst().orElseThrow();
        assertEquals(1, tdm.groupNumber().orElseThrow());
        assertEquals(10, tdm.round());
        FcttMatchReportContext copa = contexts.stream()
                .filter(context -> context.competition().startsWith("copa")).findFirst().orElseThrow();
        assertNull(copa.group(), "a group folder that is not g<n> dispatches without a group");
        assertEquals(2, counters.filesSeen());
        assertEquals(0, counters.filesSkipped());
    }

    @Test
    void fcttDoesNotReadTheLegacyGenderFolders() throws IOException {
        write("2026-2027/male/tercera-nacional/G1/jornada-1-partido-1.json", payload(1, "HOME", "AWAY", null));
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        List<FcttMatchReportContext> contexts = new ArrayList<>();

        walk(navigator(ImportSource.FCTT), "2026-2027", counters, FCTT, contexts::add);

        assertTrue(contexts.isEmpty());
        assertEquals(0, counters.filesSeen());
        assertEquals(0, navigator(ImportSource.FCTT).countReportFiles(baseFolder.resolve("2026-2027")));
    }

    @Test
    void fcttCountsAPayloadWithoutAKnownGenderAsAProcessorFailure() throws IOException {
        write("2026-2027/tdm/g1/regular/jornada_1_local_team_1_away_team_2.json", payload(1, "HOME", "AWAY", null));
        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        List<FcttMatchReportContext> contexts = new ArrayList<>();

        walk(navigator(ImportSource.FCTT), "2026-2027", counters, FCTT, contexts::add);

        assertTrue(contexts.isEmpty());
        assertEquals(1, counters.processorFailures());
        assertEquals(1, counters.issues().size());
    }

    @Test
    void fcttCountsOnlyTheReportsOfTheIngestLayout() throws IOException {
        write("2026-2027/tdm/g1/regular/jornada_1_local_team_1_away_team_2.json", payload(1, "A", "B", "masculino"));
        write("2026-2027/tdm/g1/regular/notes.json", "{}");

        assertEquals(1, navigator(ImportSource.FCTT).countReportFiles(baseFolder.resolve("2026-2027")));
    }

    @Test
    void bcnesaCountsOnlyTheReportsOfTheIngestLayout() throws IOException {
        write("2026-2027/rtb-preferent/G1/1a Fase/jornada_01_local_team_1_away_team_2.json", payload(1, "A", "B", null));
        write("2026-2027/rtb-preferent/G1/1a Fase/acta_1_page_1.json", payload(1, "A", "B", null));

        assertEquals(1, navigator(ImportSource.BCNESA).countReportFiles(baseFolder.resolve("2026-2027")));
    }

    @Test
    void recognisesTheIngestFileNamesOfEachSource() {
        FcttWebActasDirectoryNavigator bcnesa = navigator(ImportSource.BCNESA);
        FcttWebActasDirectoryNavigator fctt = navigator(ImportSource.FCTT);

        assertTrue(bcnesa.isIngestReportName("jornada_02_local_team_130_away_team_151.json"));
        assertTrue(fctt.isIngestReportName("jornada_2_local_team_130_away_team_151.json"));
        assertTrue(fctt.isIngestReportName("jornada_2_local_team_abc_away_team_151.json"));
        assertFalse(bcnesa.isIngestReportName("jornada_2_local_team_abc_away_team_151.json"));
        assertFalse(bcnesa.isIngestReportName("acta_2_page_1.json"));
        assertFalse(fctt.isIngestReportName("jornada-2-partido-1.json"));
    }

    @Test
    void onlyBcnesaAndFcttHaveAnIngestLayout() {
        assertThrows(IllegalArgumentException.class,
                () -> new FcttWebActasDirectoryNavigator(ImportSource.RFETM, new ActaParser()));
    }

    @Test
    void theWholeFileSplitterYieldsOneFixtureNamedByEquipos() {
        var acta = new ActaParser().parse(write("a.json", payload(1, "HOME", "AWAY", null)));

        List<FixtureSplitter.Fixture> fixtures = FixtureSplitter.wholeFile().split(acta);

        assertEquals(1, fixtures.size());
        assertEquals("HOME", fixtures.getFirst().homeTeamName());
        assertEquals("AWAY", fixtures.getFirst().awayTeamName());
        assertTrue(fixtures.getFirst().isResolved());
    }

    @Test
    void theLegacyBcnesaSplitterIsUsableAsTheFixtureSplitterHook() {
        var acta = new ActaParser().parse(write("b.json", payload(1, "HOME", "AWAY", null)));

        List<FixtureSplitter.Fixture> fixtures =
                new BcnesaMatchdaySplitter().asFixtureSplitter(() -> null).split(acta);

        assertEquals(1, fixtures.size(), "a report without games is one fixture and never builds the club index");
        assertEquals("HOME", fixtures.getFirst().homeTeamName());
        assertEquals("AWAY", fixtures.getFirst().awayTeamName());
    }

    private FcttWebActasDirectoryNavigator navigator(ImportSource source) {
        return new FcttWebActasDirectoryNavigator(source, new ActaParser());
    }

    private <C extends org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebFixtureContext> void walk(
            FcttWebActasDirectoryNavigator navigator, String season, FcttWebTraversalCounters counters,
            FcttWebContextFactory<C> factory, java.util.function.Consumer<C> sink) throws IOException {
        navigator.traverseSeason(baseFolder.resolve(season), season, new ImportRunContext(navigator.source(), season),
                counters, org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener.noop(),
                factory, sink);
    }

    private Path write(String relative, String content) {
        try {
            Path file = baseFolder.resolve(relative);
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String payload(Integer round, String home, String away, String gender) {
        return """
                {
                  "federacion": "Federació Catalana de Tennis Taula",
                  "jornada": %s,
                  "genero": %s,
                  "equipos": {
                    "local": { "id": null, "nombre": "%s" },
                    "visitante": { "id": null, "nombre": "%s" }
                  }
                }
                """.formatted(round, gender == null ? "null" : "\"" + gender + "\"", home, away);
    }
}
