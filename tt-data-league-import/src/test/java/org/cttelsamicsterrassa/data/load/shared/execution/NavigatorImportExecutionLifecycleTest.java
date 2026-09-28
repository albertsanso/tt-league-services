package org.cttelsamicsterrassa.data.load.shared.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubToCanonicalClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00082: the traversal summaries, metrics and run status carry the lifecycle counters, a
 * no-change run ends SUCCESS, unresolved pending fixtures keep a run out of EMPTY_RESULT, and
 * reported outcomes arrive as warnings that never fail the run.
 */
class NavigatorImportExecutionLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path baseFolder;

    private InMemoryRepositories.Matches matches;
    private List<MatchContextProcessor> rfetmProcessors;
    private NavigatorImportExecutionService service;

    @BeforeEach
    void setUp() {
        InMemoryRepositories.Teams teams = new InMemoryRepositories.Teams();
        InMemoryRepositories.PlayerSeasons playerSeasons = new InMemoryRepositories.PlayerSeasons();
        InMemoryRepositories.Lineups lineups = new InMemoryRepositories.Lineups(playerSeasons);
        InMemoryRepositories.Games games = new InMemoryRepositories.Games();
        InMemoryRepositories.SetScores setScores = new InMemoryRepositories.SetScores();
        InMemoryRepositories.DoublesPairs doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);

        rfetmProcessors = List.of(
                new RfetmTeamImportProcessor(teams),
                new RfetmPlayerImportProcessor(playerSeasons),
                new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
        List<FcttMatchReportProcessor> fcttProcessors = List.of(
                new FcttTeamImportProcessor(teams),
                new FcttPlayerImportProcessor(playerSeasons),
                new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
        List<BcnesaMatchReportProcessor> bcnesaProcessors = List.of(
                new BcnesaTeamImportProcessor(teams),
                new BcnesaPlayerImportProcessor(playerSeasons),
                new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games, doublesPairs));

        service = serviceWithoutConsolidation(fcttProcessors, bcnesaProcessors);
    }

    @Test
    void reimportingAnUnchangedPendingSnapshotSucceedsAndStillRunsConsolidation() throws Exception {
        writeRfetmActa(fixture("acta_rfetm_2026_unpublished.json"));
        ImportExecutionRequest request = rfetmRequest();

        ImportExecutionResult first = service.execute(request, ImportExecutionOptions.defaults());
        assertEquals(ImportProcessStatus.SUCCESS, first.status());
        assertEquals(1, first.metrics().lifecycle().scheduledCreated());

        RecordingRfetmClubs rfetmClubs = new RecordingRfetmClubs();
        RecordingCanonicalClubs canonical = new RecordingCanonicalClubs();
        NavigatorImportExecutionService withClubs = new NavigatorImportExecutionService(
                new RfetmActasDirectoryNavigator(rfetmProcessors, new ActaParser()),
                new BcnesaActasDirectoryNavigator(List.of(), new ActaParser()),
                new FcttActasDirectoryNavigator(List.of(), new ActaParser()),
                rfetmProcessors, List.of(), List.of(),
                null, rfetmClubs, canonical, null);

        ImportExecutionResult second = withClubs.execute(request, new ImportExecutionOptions(
                ConsolidationMode.REPORT, null, baseFolder.resolve("rfetm-teams"), 50));

        assertEquals(ImportProcessStatus.SUCCESS, second.status(), "a no-change run is not empty");
        assertEquals(0, second.metrics().lifecycle().scheduledCreated());
        assertEquals(0, second.metrics().lifecycle().rescheduled());
        assertEquals(1, matches.saved.size(), "nothing new was stored");
        assertEquals(1, rfetmClubs.calls, "consolidation still runs for a SUCCESS no-change run");
        assertEquals(1, canonical.calls);
        assertEquals(1, second.postProcessing().size(), "one clubs outcome combining both passes");
    }

    @Test
    void publishedActaUpgradesTheStoredPendingMatchAndCountsIt() throws Exception {
        Path acta = writeRfetmActa(fixture("acta_rfetm_2026_unpublished.json"));
        ImportExecutionRequest request = rfetmRequest();

        ImportExecutionResult pending = service.execute(request, ImportExecutionOptions.defaults());
        assertEquals(1, pending.metrics().lifecycle().scheduledCreated());

        Files.writeString(acta, publishedWithPendingTeams().toString());
        ImportExecutionResult published = service.execute(request, ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, published.status());
        assertEquals(1, published.metrics().lifecycle().upgradedToPlayed());
        assertEquals(0, published.metrics().lifecycle().scheduledCreated());
        assertTrue(published.warnings().isEmpty(), "an upgrade is not a reported outcome");
        assertEquals(1, matches.saved.size());
    }

    @Test
    void anEmptyFolderStillEndsEmptyResult() {
        ImportExecutionResult result = service.execute(rfetmRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.EMPTY_RESULT, result.status());
        assertEquals(ImportLifecycleCounters.ZERO, result.metrics().lifecycle());
    }

    @Test
    void aFolderOfOnlyUnparsableFilesStillEndsEmptyResult() throws IOException {
        Path folder = Files.createDirectories(
                baseFolder.resolve("2026-2027").resolve("divisio-honor").resolve("1").resolve("femenino"));
        Files.writeString(folder.resolve("acta.json"), "{ not json");

        ImportExecutionResult result = service.execute(rfetmRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.EMPTY_RESULT, result.status(),
                "a parse/structure skip is not relabelled as success");
        assertEquals(1, result.metrics().skipped());
    }

    @Test
    void anFcttFolderOfOnlyNoTeamPlaceholdersIsSuccessWithUnresolvedCounters() throws Exception {
        Path folder = Files.createDirectories(baseFolder.resolve("2026-2027").resolve("female")
                .resolve("copa-catalana-femenina-1a"));
        Files.writeString(folder.resolve("jornada-1-partido-1.json"), fixture("acta_fctt_2026_no_team_placeholder.json").toString());

        ImportExecutionResult result = service.execute(
                new ImportExecutionRequest(ImportSource.FCTT, baseFolder, Optional.empty()),
                ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status(),
                "a recognised unresolved pending fixture is not an empty result");
        assertEquals(1, result.metrics().lifecycle().unresolvedPendingFixtures());
        assertEquals(1, result.warnings().size(), "the placeholder is reported as a warning");
        assertTrue(result.issues().isEmpty(), "warnings never become issues");
    }

    @Test
    void anInvalidActaEndsSuccessWithTheInvalidCounterAndOneWarning() throws Exception {
        ObjectNode invalid = fixture("acta_rfetm_2026_unpublished.json");
        invalid.put("acta_publicada", true);
        writeRfetmActa(invalid);

        ImportExecutionResult result = service.execute(rfetmRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertEquals(1, result.metrics().lifecycle().invalidActas());
        assertEquals(0, result.metrics().lifecycle().scheduledCreated(),
                "per-acta outcomes are exclusive: the scheduled creation is counted only as INVALID");
        assertEquals(1, result.warnings().size());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void aPartialActaEndsSuccessWithThePartialCounterAndOneWarning() throws Exception {
        ObjectNode partial = publishedWithPendingTeams();
        partial.putNull("acta_publicada");
        ObjectNode lastGame = (ObjectNode) partial.withArray("partidos").get(6);
        lastGame.remove("sets");
        lastGame.remove("ganador");
        lastGame.remove("resultado_juegos");
        writeRfetmActa(partial);

        ImportExecutionResult result = service.execute(rfetmRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertEquals(1, result.metrics().lifecycle().partialActas());
        assertEquals(1, result.warnings().size());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void aPendingActaForAStoredPlayedMatchEndsSuccessWithARegressionWarningOnly() throws Exception {
        Path acta = writeRfetmActa(fixture("acta_rfetm_2026_unpublished.json"));
        ImportExecutionRequest request = rfetmRequest();
        service.execute(request, ImportExecutionOptions.defaults());
        Files.writeString(acta, publishedWithPendingTeams().toString());
        service.execute(request, ImportExecutionOptions.defaults());

        Files.writeString(acta, fixture("acta_rfetm_2026_unpublished.json").toString());
        ImportExecutionResult regression = service.execute(request, ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, regression.status());
        assertEquals(ImportLifecycleCounters.ZERO, regression.metrics().lifecycle(),
                "the regression counter is not one of the six; it surfaces as a warning");
        assertEquals(1, regression.warnings().size());
        assertTrue(regression.warnings().getFirst().message().contains("acta_publicada"));
        assertTrue(regression.issues().isEmpty());
    }

    @Test
    void theGroupOneSnapshotOfFcttTerceraNacionalReportsRoundOneAsCurrentAndIncomplete() throws Exception {
        writeFcttGroupOneSnapshot();

        ImportExecutionResult result = service.execute(fcttRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertEquals(1, result.roundProgress().size(), "one competition, group and phase was imported");
        RoundProgress row = result.roundProgress().getFirst();
        assertEquals("tercera-nacional-masculino", row.competition());
        assertEquals(1, row.groupNumber());
        assertEquals("1a Fase", row.phase());
        assertEquals(1, row.currentRound());
        assertNull(row.lastCompleteRound(), "jornada 1 still holds three pending actas");
        assertEquals(9, row.scheduledMatches());
        assertEquals(3, row.playedMatches());
    }

    @Test
    void publishingThePendingFixturesOfRoundOneMakesThatRoundComplete() throws Exception {
        writeFcttGroupOneSnapshot();
        service.execute(fcttRequest(), ImportExecutionOptions.defaults());

        publishFcttRoundOnePendingFixtures();
        ImportExecutionResult result = service.execute(fcttRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        RoundProgress row = result.roundProgress().getFirst();
        assertEquals(1, row.currentRound());
        assertEquals(1, row.lastCompleteRound(), "jornada 1 is fully played, jornada 2 is not");
        assertEquals(6, row.scheduledMatches());
        assertEquals(6, row.playedMatches());
    }

    @Test
    void progressIsInformationalAndNeverSkipsFilesOfACompleteRound() throws Exception {
        writeFcttGroupOneSnapshot();
        ImportExecutionResult first = service.execute(fcttRequest(), ImportExecutionOptions.defaults());

        publishFcttRoundOnePendingFixtures();
        ImportExecutionResult second = service.execute(fcttRequest(), ImportExecutionOptions.defaults());
        ImportExecutionResult third = service.execute(fcttRequest(), ImportExecutionOptions.defaults());

        assertEquals(12, second.metrics().filesSeen(), "a complete round does not shorten the traversal");
        assertEquals(second.metrics().filesSeen(), third.metrics().filesSeen());
        assertEquals(second.metrics().itemsDispatched(), third.metrics().itemsDispatched());
        assertEquals(second.metrics().skipped(), third.metrics().skipped());
        assertEquals(second.roundProgress(), third.roundProgress(), "the same snapshot yields the same progress");
    }

    @Test
    void aRunWithoutASeasonComputesNoProgressInsteadOfGuessingOne() throws Exception {
        writeFcttGroupOneSnapshot();

        ImportExecutionResult result = service.execute(
                new ImportExecutionRequest(ImportSource.FCTT, baseFolder, Optional.empty()),
                ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.SUCCESS, result.status());
        assertTrue(result.roundProgress().isEmpty(), "no season means no implicit season is selected");
    }

    @Test
    void aFailingProgressQueryIsReportedAsAFailureInsteadOfAnEmptyProgress() {
        NavigatorImportExecutionService failing = new NavigatorImportExecutionService(
                new RfetmActasDirectoryNavigator(rfetmProcessors, new ActaParser()),
                new BcnesaActasDirectoryNavigator(List.of(), new ActaParser()),
                new FcttActasDirectoryNavigator(List.of(), new ActaParser()),
                rfetmProcessors, List.of(), List.of(), null, null, null, null, new ThrowingProgressMatches());

        ImportExecutionResult result = failing.execute(rfetmRequest(), ImportExecutionOptions.defaults());

        assertEquals(ImportProcessStatus.FAILURE, result.status());
        assertEquals(1, result.issues().size());
        assertEquals("round-progress", result.issues().getFirst().processor());
        assertEquals("progress query failed", result.issues().getFirst().message());
        assertTrue(result.roundProgress().isEmpty());
    }

    private ImportExecutionRequest fcttRequest() {
        return new ImportExecutionRequest(ImportSource.FCTT, baseFolder, Optional.of(Season.of(2026)));
    }

    /**
     * The real FCTT 2026-2027 {@code male/tercera-nacional/G1} shape: jornada 1 with three published
     * and three pending actas, jornada 2 with six pending ones.
     */
    private void writeFcttGroupOneSnapshot() throws IOException {
        for (int partido = 1; partido <= 3; partido++) {
            writeFcttActa(publishedFcttActa(1, partido), 1, partido);
        }
        for (int partido = 4; partido <= 6; partido++) {
            writeFcttActa(pendingFcttActa(1, partido), 1, partido);
        }
        for (int partido = 1; partido <= 6; partido++) {
            writeFcttActa(pendingFcttActa(2, partido), 2, partido);
        }
    }

    private void publishFcttRoundOnePendingFixtures() throws IOException {
        for (int partido = 4; partido <= 6; partido++) {
            writeFcttActa(publishedFcttActa(1, partido), 1, partido);
        }
    }

    private ObjectNode pendingFcttActa(int jornada, int partido) {
        return annotatedFcttActa(fixture("acta_fctt_unpublished.json"), jornada, partido);
    }

    private ObjectNode publishedFcttActa(int jornada, int partido) {
        return annotatedFcttActa(fixture("acta_fctt_2026_published.json"), jornada, partido);
    }

    private static ObjectNode annotatedFcttActa(ObjectNode acta, int jornada, int partido) {
        acta.put("id_partido", "2026-2027_tercera-nacional_G1_1aFase_R" + jornada + "P" + partido);
        acta.put("jornada", jornada);
        ObjectNode equipos = (ObjectNode) acta.get("equipos");
        ((ObjectNode) equipos.get("local")).put("id", "home-" + partido).put("nombre", "PROG HOME " + partido);
        ((ObjectNode) equipos.get("visitante")).put("id", "away-" + partido).put("nombre", "PROG AWAY " + partido);
        return acta;
    }

    private Path writeFcttActa(ObjectNode acta, int jornada, int partido) throws IOException {
        Path folder = Files.createDirectories(baseFolder.resolve("2026-2027").resolve("male")
                .resolve("tercera-nacional").resolve("G1"));
        Path file = folder.resolve("jornada-" + jornada + "-partido-" + partido + ".json");
        Files.writeString(file, acta.toString());
        return file;
    }

    /** The progress query is a read that may fail; the run must report that instead of hiding it. */
    private static final class ThrowingProgressMatches
            implements org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository {

        @Override
        public List<RoundProgress> findRoundProgress(ImportSource source, Season season) {
            throw new IllegalStateException("progress query failed");
        }

        @Override
        public Optional<Match> findMatchById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Match> findMatchByExternalId(String externalId) {
            return Optional.empty();
        }

        @Override
        public Optional<Match> findMatchByNaturalKey(String competition, Season season, Integer groupNumber,
                                                     int round, String phase, UUID homeTeamId, UUID awayTeamId) {
            return Optional.empty();
        }

        @Override
        public Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId) {
            return Optional.empty();
        }

        @Override
        public List<Match> findAllMatchesByTeamIds(Collection<UUID> teamIds) {
            return List.of();
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSource(Collection<UUID> teamIds, ImportSource source) {
            return List.of();
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(Collection<UUID> teamIds,
                                                                                  ImportSource source,
                                                                                  Season season,
                                                                                  String competition) {
            return List.of();
        }

        @Override
        public void saveMatch(Match match) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public void replaceMatchContent(MatchContent content) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public void updateSchedule(UUID matchId, MatchSchedule schedule) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }

    private NavigatorImportExecutionService serviceWithoutConsolidation(
            List<FcttMatchReportProcessor> fcttProcessors,
            List<BcnesaMatchReportProcessor> bcnesaProcessors) {
        return new NavigatorImportExecutionService(
                new RfetmActasDirectoryNavigator(rfetmProcessors, new ActaParser()),
                new BcnesaActasDirectoryNavigator(bcnesaProcessors, new ActaParser()),
                new FcttActasDirectoryNavigator(fcttProcessors, new ActaParser()),
                rfetmProcessors, bcnesaProcessors, fcttProcessors,
                null, null, null, null, matches);
    }

    private ImportExecutionRequest rfetmRequest() {
        return new ImportExecutionRequest(ImportSource.RFETM, baseFolder, Optional.of(Season.of(2026)));
    }

    private Path writeRfetmActa(ObjectNode acta) throws IOException {
        Path folder = Files.createDirectories(
                baseFolder.resolve("2026-2027").resolve("divisio-honor").resolve("1").resolve("femenino"));
        Path file = folder.resolve("acta.json");
        Files.writeString(file, acta.toString());
        return file;
    }

    /** The 2026 published reference acta wearing the unpublished fixture's equipos, one and the same match. */
    private ObjectNode publishedWithPendingTeams() throws Exception {
        ObjectNode published = fixture("acta_rfetm_2026_published.json");
        ObjectNode unpublished = fixture("acta_rfetm_2026_unpublished.json");
        published.set("equipos", unpublished.get("equipos"));
        // One and the same fixture keeps its id_partido (FEAT-00085): a different value over the
        // same natural key would be reported as a fixture identity conflict instead of upgrading.
        published.put("id_partido", unpublished.get("id_partido").asText());
        return published;
    }

    private static ObjectNode fixture(String name) {
        URL resource = NavigatorImportExecutionLifecycleTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return (ObjectNode) MAPPER.readTree(Path.of(resource.toURI()).toFile());
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClubConsolidationSummary emptySummary(ImportSource source, ConsolidationMode mode) {
        return new ClubConsolidationSummary(source, mode, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    private static final class RecordingRfetmClubs extends RfetmClubConsolidationProcessor {
        private int calls;

        private RecordingRfetmClubs() {
            super(new InMemoryRepositories.Clubs(), new InMemoryRepositories.Teams(), null);
        }

        @Override
        public ClubConsolidationSummary process(Path teamsFolder, String season, ConsolidationMode mode) {
            calls++;
            return emptySummary(ImportSource.RFETM, mode);
        }
    }

    private static final class RecordingCanonicalClubs
            extends FederatedClubToCanonicalClubConsolidationProcessor {
        private int calls;

        private RecordingCanonicalClubs() {
            super(new InMemoryRepositories.Clubs(), new InMemoryRepositories.CanonicalClubs());
        }

        @Override
        public ClubConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
            calls++;
            return emptySummary(source, mode);
        }
    }
}
