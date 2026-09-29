package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00089 end to end through the real per-source processor stacks and in-memory repositories: an
 * amended PLAYED acta is detected by its content checksum and, in write mode, re-applied in place;
 * report mode performs the same detection without writes; the behaviour is opt-in and disabled by
 * default; and a legacy PLAYED match adopts the incoming checksum as a baseline instead of being
 * rewritten.
 */
class AmendedActaImportTest {

    private static final Path ANY_FILE = Path.of("acta.json");
    private static final String RFETM_SEASON = "2026-2027";
    private static final String FCTT_SEASON = "2026-2027";

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;

    private List<MatchContextProcessor> rfetmProcessors;
    private List<FcttMatchReportProcessor> fcttProcessors;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);

        rfetmProcessors = List.of(
                new RfetmTeamImportProcessor(teams),
                new RfetmPlayerImportProcessor(playerSeasons),
                new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
        fcttProcessors = List.of(
                new FcttTeamImportProcessor(teams),
                new FcttPlayerImportProcessor(playerSeasons),
                new FcttMatchImportProcessor(teams, playerSeasons, matches, lineups, games,
                        setScores, doublesPairs));
    }

    @Test
    void rfetmCreateStoresChecksumAndAReimportIsKept() {
        Acta acta = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(acta, runContext(ImportSource.RFETM, null)));
        Match stored = matches.saved.getFirst();
        assertEquals(MatchStatus.PLAYED, stored.getStatus());
        assertTrue(stored.getSourceChecksum().startsWith("v1:"));
        String checksum = stored.getSourceChecksum();

        ImportRunContext reimport = runContext(ImportSource.RFETM, AmendedActaMode.WRITE);
        runRfetm(rfetmContext(acta, reimport));

        assertEquals(1, count(reimport, MatchLifecycleOutcome.PLAYED_KEPT));
        assertEquals(1, matches.saved.size());
        assertEquals(checksum, matches.saved.getFirst().getSourceChecksum());
        assertTrue(reimport.reportedMatchIssues().isEmpty(), "an unchanged acta is not an amendment");
    }

    @Test
    void rfetmAmendedActaIsIgnoredWhenDetectionIsDisabled() {
        Acta original = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(original, runContext(ImportSource.RFETM, null)));
        Match stored = matches.saved.getFirst();
        String checksum = stored.getSourceChecksum();
        int gameCount = games.saved.size();

        ImportRunContext disabled = runContext(ImportSource.RFETM, null);
        runRfetm(rfetmContext(amended(original), disabled));

        assertEquals(1, count(disabled, MatchLifecycleOutcome.PLAYED_KEPT));
        assertEquals(checksum, matches.saved.getFirst().getSourceChecksum());
        assertEquals(gameCount, games.saved.size());
        assertTrue(disabled.reportedMatchIssues().isEmpty());
    }

    @Test
    void rfetmAmendedActaIsReappliedInWriteModeAndIsIdempotent() {
        Acta original = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(original, runContext(ImportSource.RFETM, null)));
        Match stored = matches.saved.getFirst();
        UUID id = stored.getId();
        String fixtureId = stored.getSourceFixtureId();
        String checksum = stored.getSourceChecksum();
        List<RoundProgress> progressBefore = matches.findRoundProgress(ImportSource.RFETM, Season.of(2026));

        ImportRunContext write = runContext(ImportSource.RFETM, AmendedActaMode.WRITE);
        runRfetm(rfetmContext(amended(original), write));

        assertEquals(1, count(write, MatchLifecycleOutcome.PLAYED_AMENDED));
        assertEquals(1, write.reportedMatchIssues().size());
        assertEquals("amended acta re-applied", write.reportedMatchIssues().getFirst().message());
        Match after = matches.findMatchById(id).orElseThrow();
        assertEquals(id, after.getId());
        assertEquals(fixtureId, after.getSourceFixtureId());
        assertNotEquals(checksum, after.getSourceChecksum());
        assertEquals(progressBefore, matches.findRoundProgress(ImportSource.RFETM, Season.of(2026)),
                "a re-apply never moves a match round");

        ImportRunContext rerun = runContext(ImportSource.RFETM, AmendedActaMode.WRITE);
        runRfetm(rfetmContext(amended(original), rerun));
        assertEquals(1, count(rerun, MatchLifecycleOutcome.PLAYED_KEPT), "running the same acta again is idempotent");
        assertEquals(after.getSourceChecksum(), matches.findMatchById(id).orElseThrow().getSourceChecksum());
    }

    @Test
    void rfetmAmendedActaIsReportedWithoutWritesInReportMode() {
        Acta original = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(original, runContext(ImportSource.RFETM, null)));
        Match stored = matches.saved.getFirst();
        String checksum = stored.getSourceChecksum();
        int gameCount = games.saved.size();

        ImportRunContext report = runContext(ImportSource.RFETM, AmendedActaMode.REPORT);
        runRfetm(rfetmContext(amended(original), report));

        assertEquals(1, count(report, MatchLifecycleOutcome.PLAYED_AMENDMENT_REPORTED));
        assertEquals("amended acta detected (report mode)", report.reportedMatchIssues().getFirst().message());
        assertEquals(checksum, matches.findMatchById(stored.getId()).orElseThrow().getSourceChecksum());
        assertEquals(gameCount, games.saved.size(), "report mode performs no write");
    }

    @Test
    void rfetmLegacyPlayedMatchAdoptsTheChecksumWithoutTouchingItsContent() {
        Acta original = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(original, runContext(ImportSource.RFETM, null)));
        Match stored = matches.saved.getFirst();
        matches.saved.set(0, stored.withSourceChecksum(null));
        int gameCount = games.saved.size();
        ZonedDateTime date = stored.getDateTime();

        ImportRunContext write = runContext(ImportSource.RFETM, AmendedActaMode.WRITE);
        runRfetm(rfetmContext(original, write));

        assertEquals(1, count(write, MatchLifecycleOutcome.PLAYED_KEPT));
        assertTrue(write.reportedMatchIssues().isEmpty(), "baseline adoption is not a reported amendment");
        Match after = matches.findMatchById(stored.getId()).orElseThrow();
        assertTrue(after.getSourceChecksum().startsWith("v1:"));
        assertEquals(date, after.getDateTime());
        assertEquals(gameCount, games.saved.size());
    }

    @Test
    void rfetmPendingActaOnAStoredPlayedMatchIsStillARegressionInEveryMode() {
        Acta original = acta("acta_rfetm_2026_published.json");
        runRfetm(rfetmContext(original, runContext(ImportSource.RFETM, null)));
        int gameCount = games.saved.size();

        for (AmendedActaMode mode : new AmendedActaMode[] {null, AmendedActaMode.WRITE, AmendedActaMode.REPORT}) {
            ImportRunContext run = runContext(ImportSource.RFETM, mode);
            runRfetm(rfetmContext(withPublished(original, false), run));
            assertEquals(1, count(run, MatchLifecycleOutcome.REGRESSION_REPORTED), "mode " + mode);
        }
        assertEquals(gameCount, games.saved.size(), "a regression never writes");
    }

    @Test
    void fcttAmendedActaIsReappliedInWriteMode() {
        Acta original = acta("acta_fctt_2026_published.json");
        runFctt(fcttContext(original, runContext(ImportSource.FCTT, null)));
        Match stored = matches.saved.getFirst();
        assertEquals(MatchStatus.PLAYED, stored.getStatus());
        assertTrue(stored.getSourceChecksum().startsWith("v1:"));
        UUID id = stored.getId();
        String checksum = stored.getSourceChecksum();

        ImportRunContext write = runContext(ImportSource.FCTT, AmendedActaMode.WRITE);
        runFctt(fcttContext(amended(original), write));

        assertEquals(1, count(write, MatchLifecycleOutcome.PLAYED_AMENDED));
        Match after = matches.findMatchById(id).orElseThrow();
        assertEquals(id, after.getId());
        assertNotEquals(checksum, after.getSourceChecksum());
        assertEquals(ImportSource.FCTT, after.getSource());
    }

    // --- helpers --------------------------------------------------------------------------

    private static int count(ImportRunContext runContext, MatchLifecycleOutcome outcome) {
        return runContext.matchOutcomeCounts().getOrDefault(outcome, 0);
    }

    private void runRfetm(MatchReportContext context) {
        rfetmProcessors.forEach(processor -> processor.process(context));
    }

    private void runFctt(FcttMatchReportContext context) {
        fcttProcessors.forEach(processor -> processor.process(context));
    }

    private static ImportRunContext runContext(ImportSource source, AmendedActaMode mode) {
        String season = source == ImportSource.FCTT ? FCTT_SEASON : RFETM_SEASON;
        return mode == null
                ? new ImportRunContext(source, season)
                : new ImportRunContext(source, season, mode);
    }

    private static MatchReportContext rfetmContext(Acta acta, ImportRunContext runContext) {
        return new MatchReportContext(RFETM_SEASON, "divisio-honor", "3", "femenino",
                RfetmClubKey.ofFederationId(acta.teams().home().rfetmId(), null),
                RfetmClubKey.ofFederationId(acta.teams().away().rfetmId(), null),
                ANY_FILE, acta, runContext);
    }

    private static FcttMatchReportContext fcttContext(Acta acta, ImportRunContext runContext) {
        return new FcttMatchReportContext(FCTT_SEASON, "male", "tercera-nacional", "G1", acta.round(),
                ANY_FILE, acta, runContext);
    }

    private static Acta acta(String name) {
        return new ActaParser().parse(fixture(name));
    }

    private static Path fixture(String name) {
        URL resource = AmendedActaImportTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The acta with its first game's result changed, which is exactly what a correction rewrites. */
    private static Acta amended(Acta acta) {
        List<ActaGame> games = new ArrayList<>(acta.games());
        ActaGame first = games.getFirst();
        ActaGame changed = new ActaGame(first.number(), first.type(), first.crossover(), first.home(),
                first.away(), first.sets(), new ActaScore(4, 0), ActaGame.WINNER_HOME,
                first.cumulativeScore(), first.notPlayed(), first.reason());
        games.set(0, changed);
        return withGames(acta, games);
    }

    private static Acta withGames(Acta acta, List<ActaGame> games) {
        return copy(acta, b -> b.games = games);
    }

    private static Acta withPublished(Acta acta, boolean published) {
        return copy(acta, b -> b.published = published ? Boolean.TRUE : Boolean.FALSE);
    }

    /** Mutates one {@link Acta} component at a time without touching the record constructor's arguments. */
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
        private java.time.LocalDate date;
        private java.time.LocalTime time;
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