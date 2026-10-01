package org.cttelsamicsterrassa.data.load.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewClassification;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResourceStatus;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewScopeChanges;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.Resource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest;
import org.cttelsamicsterrassa.data.load.shared.execution.NavigatorImportExecutionService;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.preview.NavigatorBackedImportResourcePreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00088: the wired preview service over a real FCTT snapshot tree, against a read-only
 * {@link MatchRepository} decorator that throws on any write. It pins the projected acta buckets,
 * per-scope changes, current and projected jornada progress, duplicate {@code id_partido} detection,
 * the unresolved-fixture count and the strict read-only behaviour of the preview.
 */
class IncrementalPreviewServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera-nacional-masculino";

    @TempDir
    Path baseFolder;

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private InMemoryRepositories.Matches matches;

    private NavigatorImportExecutionService importService;
    private NavigatorBackedImportResourcePreviewService previewService;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);
        MatchRepository readOnly = new ReadOnlyMatches(matches);

        List<FcttMatchReportProcessor> writableFctt = List.of(
                new FcttTeamImportProcessor(teams),
                new FcttPlayerImportProcessor(playerSeasons),
                new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games, setScores,
                        doublesPairs));
        ActaParser parser = new ActaParser();
        importService = new NavigatorImportExecutionService(
                new RfetmActasDirectoryNavigator(List.of(), parser),
                new BcnesaActasDirectoryNavigator(List.of(), parser),
                new FcttActasDirectoryNavigator(writableFctt, parser),
                List.of(), List.of(), writableFctt,
                null, null, null, null, matches);

        RfetmMatchImportProcessor rfetmPreview = new RfetmMatchImportProcessor(teams, playerSeasons, readOnly,
                lineups, games, setScores, doublesPairs);
        BcnesaMatchImportProcessor bcnesaPreview = new BcnesaMatchImportProcessor(teams, playerSeasons, readOnly,
                lineups, games, setScores, doublesPairs);
        FcttMatchImportProcessor fcttPreview = new FcttMatchImportProcessor(teams, playerSeasons, readOnly,
                lineups, games, setScores, doublesPairs);
        previewService = new NavigatorBackedImportResourcePreviewService(
                new RfetmActasDirectoryNavigator(List.of(), parser),
                new BcnesaActasDirectoryNavigator(List.of(), parser),
                new FcttActasDirectoryNavigator(List.of(), parser),
                rfetmPreview, bcnesaPreview, fcttPreview, readOnly);
    }

    @Test
    void emptyStoreProjectsBucketsChangesAndProgressWithoutWriting() throws Exception {
        writeFcttGroupOneSnapshot();

        ImportPreviewResult result = previewService.preview(fcttResource());

        assertEquals(ImportPreviewStatus.SUCCESS, result.status());
        ImportPreviewClassification classification = result.classification();
        assertEquals(3, classification.actas().published());
        assertEquals(9, classification.actas().unpublished());
        assertEquals(0, classification.actas().unresolved());
        PreviewScopeChanges scope = scope(classification, TERCERA, 1);
        assertEquals(3, scope.newPlayed());
        assertEquals(9, scope.newScheduled());
        assertTrue(classification.currentProgress().isEmpty(), "an empty store has no current progress");
        assertEquals(1, classification.projectedProgress().size());
        RoundProgress projected = classification.projectedProgress().getFirst();
        assertEquals(1, projected.currentRound());
        assertEquals(null, projected.lastCompleteRound());
        assertEquals(3, projected.playedMatches());
        assertEquals(9, projected.scheduledMatches());
        assertTrue(matches.saved.isEmpty(), "the preview never writes");
    }

    @Test
    void secondSnapshotProjectsUpgradesAndCompletedRound() throws Exception {
        writeFcttGroupOneSnapshot();
        importService.execute(new ImportExecutionRequest(ImportSource.FCTT, baseFolder, Optional.of(SEASON)),
                ImportExecutionOptions.defaults());
        int storedAfterImport = matches.saved.size();
        assertEquals(12, storedAfterImport, "3 played + 9 scheduled");

        // Publish the three pending jornada-1 fixtures; jornada 2 stays pending.
        for (int partido = 4; partido <= 6; partido++) {
            writeFcttActa(publishedFcttActa(1, partido), 1, partido);
        }

        ImportPreviewResult result = previewService.preview(fcttResource());

        ImportPreviewClassification classification = result.classification();
        PreviewScopeChanges scope = scope(classification, TERCERA, 1);
        assertEquals(3, scope.upgrades(), "the three published jornada-1 fixtures upgrade their stored match");
        assertEquals(3, scope.playedKept(), "the already played jornada-1 fixtures are kept");
        assertEquals(6, scope.unchanged(), "jornada 2 is still pending and unchanged");
        assertEquals(6, classification.actas().published());
        assertEquals(6, classification.actas().unpublished());
        RoundProgress projected = scopeRow(classification.projectedProgress(), TERCERA, 1);
        assertEquals(6, projected.playedMatches());
        assertEquals(6, projected.scheduledMatches());
        assertEquals(1, projected.currentRound());
        assertEquals(1, projected.lastCompleteRound(), "jornada 1 has no scheduled match left");
        assertEquals(storedAfterImport, matches.saved.size(), "the preview never writes");
    }

    @Test
    void duplicateIdPartidoIsFlaggedOnceAndProjectedOnce() throws Exception {
        ObjectNode first = pendingFcttActa(1, 1);
        ObjectNode second = pendingFcttActa(1, 2);
        second.put("id_partido", first.get("id_partido").asText());
        writeFcttActa(first, 1, 1);
        writeFcttActa(second, 1, 2);

        ImportPreviewResult result = previewService.preview(fcttResource());

        ImportPreviewClassification classification = result.classification();
        assertEquals(1, classification.duplicateFixtureIds().size());
        assertEquals(2, classification.duplicateFixtureIds().getFirst().locations().size());
        assertTrue(result.validationFindings().stream()
                        .anyMatch(finding -> "warning".equals(finding.severity())
                                && finding.message().contains("Duplicate id_partido")),
                "a duplicate id_partido is surfaced as a warning");
        RoundProgress projected = scopeRow(classification.projectedProgress(), TERCERA, 1);
        assertEquals(1, projected.scheduledMatches(), "the duplicated fixture counts once in the projection");
    }

    @Test
    void unresolvedPendingFixtureIsCountedOnce() throws Exception {
        writeFcttFile(fixture("acta_fctt_2026_no_team_placeholder.json"), "female", "copa-catalana-femenina-1a",
                null, "jornada-1-partido-1.json");

        ImportPreviewResult result = previewService.preview(fcttResource());

        assertEquals(1, result.classification().actas().unresolved());
        assertEquals(0, result.classification().actas().published());
        assertTrue(matches.saved.isEmpty());
    }

    // --- fixtures ------------------------------------------------------------------------------

    private ImportResource fcttResource() {
        Resource resource = Resource.createExisting(UUID.randomUUID(), "ACTAS", "import/FCTT/ACTAS", baseFolder);
        return ImportResource.createExisting(UUID.randomUUID(), resource, Optional.empty(), ResourceType.ACTAS,
                ZonedDateTime.now(), Optional.empty(), SEASON, ImportSource.FCTT, ImportResourceStatus.PENDING);
    }

    private static PreviewScopeChanges scope(ImportPreviewClassification classification, String competition,
                                             Integer groupNumber) {
        return classification.changes().stream()
                .filter(change -> competition.equals(change.competition())
                        && java.util.Objects.equals(groupNumber, change.groupNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no scope " + competition + " group " + groupNumber));
    }

    private static RoundProgress scopeRow(List<RoundProgress> progress, String competition, Integer groupNumber) {
        return progress.stream()
                .filter(row -> competition.equals(row.competition())
                        && java.util.Objects.equals(groupNumber, row.groupNumber()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no progress row " + competition + " group " + groupNumber));
    }

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
        return writeFcttFile(acta, "male", "tercera-nacional", "G1",
                "jornada-" + jornada + "-partido-" + partido + ".json");
    }

    private Path writeFcttFile(ObjectNode acta, String gender, String competition, String group, String fileName)
            throws IOException {
        Path folder = group == null
                ? Files.createDirectories(baseFolder.resolve("2026-2027").resolve(gender).resolve(competition))
                : Files.createDirectories(baseFolder.resolve("2026-2027").resolve(gender).resolve(competition)
                        .resolve(group));
        Path file = folder.resolve(fileName);
        Files.writeString(file, acta.toString());
        return file;
    }

    private static ObjectNode fixture(String name) {
        URL resource = IncrementalPreviewServiceTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return (ObjectNode) MAPPER.readTree(Path.of(resource.toURI()).toFile());
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A read-only {@link MatchRepository} that delegates every read to the in-memory store and throws
     * on any write, so a preview that writes fails the test loudly.
     */
    private static final class ReadOnlyMatches implements MatchRepository {

        private final InMemoryRepositories.Matches delegate;

        private ReadOnlyMatches(InMemoryRepositories.Matches delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Match> findMatchById(UUID id) {
            return delegate.findMatchById(id);
        }

        @Override
        public Optional<Match> findMatchByExternalId(String externalId) {
            return delegate.findMatchByExternalId(externalId);
        }

        @Override
        public Optional<Match> findMatchByNaturalKey(String competition, Season season, Integer groupNumber,
                                                     int round, String phase, UUID homeTeamId, UUID awayTeamId) {
            return delegate.findMatchByNaturalKey(competition, season, groupNumber, round, phase, homeTeamId,
                    awayTeamId);
        }

        @Override
        public Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId) {
            return delegate.findBySourceFixtureId(source, sourceFixtureId);
        }

        @Override
        public List<Match> findAllMatchesByTeamIds(Collection<UUID> teamIds) {
            return delegate.findAllMatchesByTeamIds(teamIds);
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSource(Collection<UUID> teamIds, ImportSource source) {
            return delegate.findAllMatchesByTeamIdsAndSource(teamIds, source);
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(Collection<UUID> teamIds,
                                                                                   ImportSource source,
                                                                                   Season season,
                                                                                   String competition) {
            return delegate.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(teamIds, source, season,
                    competition);
        }

        @Override
        public List<RoundProgress> findRoundProgress(ImportSource source, Season season) {
            return delegate.findRoundProgress(source, season);
        }

        @Override
        public List<RoundStatusCount> findRoundStatusCounts(ImportSource source, Season season) {
            return delegate.findRoundStatusCounts(source, season);
        }

        @Override
        public List<Match> findMatchesBySourceSeasonAndStatus(ImportSource source, Season season,
                                                              MatchStatus status) {
            return delegate.findMatchesBySourceSeasonAndStatus(source, season, status);
        }

        @Override
        public List<Match> findMatchesBySourceSeasonAndCompetition(ImportSource source, Season season,
                                                                   String competition) {
            return delegate.findMatchesBySourceSeasonAndCompetition(source, season, competition);
        }

        @Override
        public List<Match> findMatchesBySourceSeasonAndDateRange(ImportSource source, Season season,
                                                                 java.time.LocalDate fromInclusive,
                                                                 java.time.LocalDate toExclusive) {
            return delegate.findMatchesBySourceSeasonAndDateRange(source, season, fromInclusive, toExclusive);
        }

        @Override
        public void saveMatch(Match match) {
            throw new UnsupportedOperationException("preview must not write");
        }

        @Override
        public void saveMatches(Collection<Match> matches) {
            throw new UnsupportedOperationException("preview must not write");
        }

        @Override
        public void replaceMatchContent(MatchContent content) {
            throw new UnsupportedOperationException("preview must not write");
        }

        @Override
        public void updateSchedule(UUID matchId, MatchSchedule schedule) {
            throw new UnsupportedOperationException("preview must not write");
        }

        @Override
        public void recordSourceChecksum(UUID matchId, String sourceChecksum) {
            throw new UnsupportedOperationException("preview must not write");
        }
    }
}
