package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewActaCounts;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewScopeChanges;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.preview.FixturePreview;
import org.cttelsamicsterrassa.data.load.shared.preview.IncrementalPreviewCollector;
import org.cttelsamicsterrassa.data.load.shared.preview.PreviewChange;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00088: the per-source {@code preview} entry points and the {@link IncrementalPreviewCollector}
 * buckets, per-scope change counts, projection deltas, duplicate detection and read-only behaviour,
 * exercised directly against the in-memory repositories.
 */
class IncrementalPreviewCollectorTest {

    private static final Path ANY_FILE = Path.of("acta.json");
    private static final Season SEASON = Season.of(2026);

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private InMemoryRepositories.Matches matches;

    private RfetmMatchImportProcessor rfetmProcessor;
    private FcttMatchImportProcessor fcttProcessor;
    private BcnesaMatchImportProcessor bcnesaProcessor;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);
        rfetmProcessor = new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                setScores, doublesPairs);
        fcttProcessor = new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                setScores, doublesPairs);
        bcnesaProcessor = new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                doublesPairs);
    }

    // --- buckets -----------------------------------------------------------------------------

    @Test
    void rfetmBucketsPublishedUnpublishedAndPartialByClassification() {
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");

        collector.add(rfetmProcessor.preview(rfetmContext("2023-2024", "super-divisio", "1", "masculino",
                acta("acta_singles.json"), runContext)));
        collector.add(rfetmProcessor.preview(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext)));
        collector.add(rfetmProcessor.preview(rfetmContext("2023-2024", "super-divisio", "2", "masculino",
                actaSinglesWithUnfinishedLastGame(), runContext)));

        PreviewActaCounts counts = collector.actaCounts();
        assertEquals(1, counts.published(), "the played acta is published");
        assertEquals(1, counts.unpublished(), "the pending acta is unpublished");
        assertEquals(1, counts.partial(), "a published acta with an unfinished game is partial");
        assertEquals(0, counts.unresolved());
    }

    @Test
    void rfetmInvalidActaIsBucketedAsInvalid() {
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");

        Acta publishedNoGames = withGames(acta("acta_rfetm_2026_published.json"), List.of());
        collector.add(rfetmProcessor.preview(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                publishedNoGames, runContext)));

        assertEquals(1, collector.actaCounts().invalid());
        assertEquals(0, collector.actaCounts().published());
    }

    @Test
    void bcnesaBucketsPublishedAndUnpublishedPerFixture() {
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2020-2021");

        Acta matchday = acta("acta_matchday.json");
        collector.add(bcnesaProcessor.preview(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                matchday, matchday.games(), runContext)));
        Acta pending = acta("acta_bcnesa_2026_unpublished.json");
        collector.add(bcnesaProcessor.preview(bcnesaContext("2026-2027", "1a Comarcal", "G1", "1a Fase",
                pending, pending.games(), runContext)));

        PreviewActaCounts counts = collector.actaCounts();
        assertEquals(1, counts.published());
        assertEquals(1, counts.unpublished());
    }

    // --- changes and projection ---------------------------------------------------------------

    @Test
    void unregisteredTeamsProjectCreationAndCountPendingRegistration() {
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        Acta pending = acta("acta_fctt_unpublished.json");
        FixturePreview preview = fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1",
                pending, runContext));
        collector.add(preview);

        assertTrue(preview.teamsPendingRegistration(), "the teams are not registered yet");
        assertEquals(PreviewChange.NEW_SCHEDULED, preview.change(), "a pending acta would be created scheduled");
        assertEquals(1, collector.teamsPendingRegistration());
        assertEquals(1, scopeChanges(collector, "tercera-nacional-masculino").newScheduled());
    }

    @Test
    void pendingOverStoredPlayedProjectsRegressionWithoutMovingTheProjection() {
        registerFcttTeams();
        Match stored = storeFctt("1a Fase", 1, MatchStatus.PLAYED, date(2026, 9, 26),
                "2026-2027_tercera-nacional_G1_1aFase_151-123_1");
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        Acta pending = acta("acta_fctt_unpublished.json");
        FixturePreview preview = fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1",
                pending, runContext));
        collector.add(preview);

        assertEquals(PreviewChange.REGRESSION, preview.change());
        assertEquals(1, scopeChanges(collector, "tercera-nacional-masculino").regressions());
        List<org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount> projected =
                collector.project(matches.findRoundStatusCounts(ImportSource.FCTT, SEASON));
        assertEquals(1, projected.size(), "a regression never rewrites the stored PLAYED match");
        assertEquals(MatchStatus.PLAYED, projected.getFirst().status());
        assertNotNull(stored);
    }

    @Test
    void pendingWithChangedDateOverStoredScheduledProjectsReschedule() {
        registerFcttTeams();
        storeFctt("1a Fase", 1, MatchStatus.SCHEDULED, date(2026, 9, 20),
                "2026-2027_tercera-nacional_G1_1aFase_151-123_1");
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        Acta pending = acta("acta_fctt_unpublished.json"); // fecha 2026-09-26 differs from the stored 2026-09-20
        FixturePreview preview = fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1",
                pending, runContext));
        collector.add(preview);

        assertEquals(PreviewChange.RESCHEDULE, preview.change());
        assertEquals(1, scopeChanges(collector, "tercera-nacional-masculino").reschedules());
    }

    @Test
    void driftedFixtureIdIsProjectedAsIdentityConflict() {
        registerFcttTeams();
        // The stored fixture owns id_partido X at round 1; the incoming acta wears X but is round 2.
        storeFctt("1a Fase", 1, MatchStatus.SCHEDULED, date(2026, 9, 26), "DUP-ID");
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        Acta drifted = withMatchId(withRound(acta("acta_fctt_unpublished.json"), 2), "DUP-ID");
        FixturePreview preview = fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1",
                drifted, runContext));
        collector.add(preview);

        assertEquals(PreviewChange.IDENTITY_CONFLICT, preview.change());
        assertEquals(1, collector.identityConflicts().size());
        assertEquals(1, scopeChanges(collector, "tercera-nacional-masculino").identityConflicts());
    }

    @Test
    void duplicateFixtureIdIsDetectedAcrossTwoPreviews() {
        IncrementalPreviewCollector collector = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        Acta acta = withMatchId(acta("acta_fctt_unpublished.json"), "SAME-ID");
        collector.add(fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1", acta, runContext)));
        collector.add(fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1", acta, runContext)));

        assertEquals(1, collector.duplicateFixtureIds().size());
        assertEquals("SAME-ID", collector.duplicateFixtureIds().getFirst().sourceFixtureId());
        assertEquals(2, collector.duplicateFixtureIds().getFirst().locations().size());
    }

    // --- read-only ----------------------------------------------------------------------------

    @Test
    void previewNeverRecordsSnapshotFixturesOrOutcomesAndNeverWrites() {
        registerFcttTeams();
        storeFctt("1a Fase", 1, MatchStatus.SCHEDULED, date(2026, 9, 20),
                "2026-2027_tercera-nacional_G1_1aFase_151-123_1");
        int matchesBefore = matches.saved.size();
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");

        fcttProcessor.preview(fcttContext("2026-2027", "tercera-nacional", "G1",
                acta("acta_fctt_unpublished.json"), runContext));

        assertEquals(matchesBefore, matches.saved.size(), "preview stores nothing");
        assertTrue(runContext.snapshotFixtures().fixtureIds().isEmpty(), "no snapshot ledger entry");
        assertTrue(runContext.matchOutcomeCounts().isEmpty(), "no outcome counter entry");
        assertTrue(lineups.saved.isEmpty());
        assertTrue(games.saved.isEmpty());
    }

    // --- helpers ------------------------------------------------------------------------------

    private static PreviewScopeChanges scopeChanges(IncrementalPreviewCollector collector, String competition) {
        return collector.scopeChanges().stream()
                .filter(change -> competition.equals(change.competition()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no scope changes for " + competition));
    }

    private void registerFcttTeams() {
        Acta acta = acta("acta_fctt_unpublished.json");
        teams.saveTeam(Team.createNew(ImportSource.FCTT, acta.teams().home().name(), SEASON, null));
        teams.saveTeam(Team.createNew(ImportSource.FCTT, acta.teams().away().name(), SEASON, null));
    }

    private Match storeFctt(String phase, int round, MatchStatus status, ZonedDateTime dateTime,
                            String sourceFixtureId) {
        Acta acta = acta("acta_fctt_unpublished.json");
        Team home = teams.findTeamByNameAndSeasonAndSource(acta.teams().home().name(), SEASON,
                ImportSource.FCTT).orElseThrow();
        Team away = teams.findTeamByNameAndSeasonAndSource(acta.teams().away().name(), SEASON,
                ImportSource.FCTT).orElseThrow();
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.FCTT)
                .sourceFixtureId(sourceFixtureId)
                .competition("tercera-nacional-masculino")
                .season(SEASON)
                .groupNumber(1)
                .round(round)
                .phase(phase)
                .dateTime(dateTime)
                .homeTeam(home)
                .awayTeam(away)
                .status(status);
        if (status == MatchStatus.PLAYED) {
            builder.homeGamesWon(3).awayGamesWon(0).winnerTeam(home);
        }
        Match match = builder.createExisting();
        matches.saveMatch(match);
        return match;
    }

    private static ZonedDateTime date(int year, int month, int day) {
        return LocalDate.of(year, month, day).atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);
    }

    private static MatchReportContext rfetmContext(String season, String league, String day, String sex,
                                                   Acta acta, ImportRunContext runContext) {
        return new MatchReportContext(season, league, day, sex,
                RfetmClubKey.ofFederationId(acta.teams().home().rfetmId(), null),
                RfetmClubKey.ofFederationId(acta.teams().away().rfetmId(), null),
                ANY_FILE, acta, runContext);
    }

    private static FcttMatchReportContext fcttContext(String season, String league, String group,
                                                      Acta acta, ImportRunContext runContext) {
        return new FcttMatchReportContext(season, "male", league, group, acta.round(), ANY_FILE, acta,
                runContext);
    }

    private static BcnesaMatchReportContext bcnesaContext(String season, String league, String group,
                                                          String phase, Acta acta, List<ActaGame> games,
                                                          ImportRunContext runContext) {
        return new BcnesaMatchReportContext(season, league, group, phase, acta.round(), 0,
                acta.teams() == null ? null : acta.teams().home().name(),
                acta.teams() == null ? null : acta.teams().away().name(),
                ANY_FILE, acta, games, runContext);
    }

    private static Acta acta(String name) {
        return new ActaParser().parse(fixture(name));
    }

    private static Path fixture(String name) {
        URL resource = IncrementalPreviewCollectorTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** acta_singles with its last game stripped of every result: legacy PARTIAL shape. */
    private static Acta actaSinglesWithUnfinishedLastGame() {
        Acta original = acta("acta_singles.json");
        List<ActaGame> games = original.games().stream()
                .map(game -> game.number() == original.games().size()
                        ? new ActaGame(game.number(), game.type(), game.crossover(), game.home(), game.away(),
                                List.of(), null, null, game.cumulativeScore(), game.notPlayed(), game.reason())
                        : game)
                .toList();
        return withGames(original, games);
    }

    private static Acta withGames(Acta acta, List<ActaGame> games) {
        return copy(acta, copy -> copy.games = games);
    }

    private static Acta withMatchId(Acta acta, String matchId) {
        return copy(acta, copy -> copy.matchId = matchId);
    }

    private static Acta withRound(Acta acta, Integer round) {
        return copy(acta, copy -> copy.round = round);
    }

    private static Acta copy(Acta acta, java.util.function.Consumer<ActaCopy> mutator) {
        ActaCopy copy = new ActaCopy(acta);
        mutator.accept(copy);
        return copy.toActa();
    }

    private static final class ActaCopy {
        private String matchId;
        private Boolean published;
        private String federation;
        private String season;
        private String competition;
        private Integer group;
        private Integer round;
        private String phase;
        private String gender;
        private LocalDate date;
        private LocalTime time;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaVenue venue;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeams teams;
        private Boolean abcIsHome;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaOfficials officials;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineups lineups;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaDoubles doubles;
        private List<ActaGame> games;
        private org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaFinalResult finalResult;
        private Boolean protested;

        private ActaCopy(Acta acta) {
            matchId = acta.matchId();
            published = acta.published();
            federation = acta.federation();
            season = acta.season();
            competition = acta.competition();
            group = acta.group();
            round = acta.round();
            phase = acta.phase();
            gender = acta.gender();
            date = acta.date();
            time = acta.time();
            venue = acta.venue();
            teams = acta.teams();
            abcIsHome = acta.abcIsHome();
            officials = acta.officials();
            lineups = acta.lineups();
            doubles = acta.doubles();
            games = acta.games();
            finalResult = acta.finalResult();
            protested = acta.protested();
        }

        private Acta toActa() {
            return new Acta(matchId, published, federation, season, competition, group, round, phase,
                    gender, date, time, venue, teams, abcIsHome, officials, lineups, doubles, games,
                    finalResult, protested);
        }
    }
}
