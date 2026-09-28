package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaTeamImportProcessor;
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
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00081 lifecycle coverage through the real per-source processor stacks: create, upgrade,
 * reschedule, idempotent re-import and regression for RFETM, FCTT and BCNESA, plus the placeholder
 * and doubles cases the analysis calls out.
 */
class MatchLifecycleImportProcessorsTest {

    private static final Path ANY_FILE = Path.of("acta.json");

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;

    private List<MatchContextProcessor> rfetmProcessors;
    private List<FcttMatchReportProcessor> fcttProcessors;
    private List<BcnesaMatchReportProcessor> bcnesaProcessors;

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
        bcnesaProcessors = List.of(
                new BcnesaTeamImportProcessor(teams),
                new BcnesaPlayerImportProcessor(playerSeasons),
                new BcnesaMatchImportProcessor(teams, playerSeasons, matches, lineups, games, doublesPairs));
    }

    // --- RFETM -----------------------------------------------------------------------------

    @Test
    void rfetmUnpublishedActaCreatesScheduledMatchWithNoChildren() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext));

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam());
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertTrue(lineups.saved.isEmpty());
        assertTrue(games.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.SCHEDULED_CREATED));
    }

    @Test
    void rfetmRecordsTheFixtureAsSeenBeforeTheUnregisteredTeamEarlyReturn() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        RfetmMatchImportProcessor matchProcessor = new RfetmMatchImportProcessor(teams, playerSeasons, matches,
                lineups, games, setScores, doublesPairs);

        // teams is empty, so neither side resolves and the processor returns before storing anything.
        matchProcessor.process(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext));

        assertTrue(matches.saved.isEmpty(), "an unregistered team stores no match");
        var seen = runContext.snapshotFixtures();
        assertTrue(seen.containsFixtureId("2026-2027_divisio-honor_G3_J1_20201878-1052"),
                "the fixture id is recorded before the early return, so reconciliation sees it by id_partido");
        assertTrue(seen.naturalKeys().isEmpty(), "no natural key is recorded without resolved teams");
    }

    @Test
    void rfetmPublishedActaUpgradesStoredScheduledMatchKeepingItsId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        MatchReportContext pending = rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext);
        runRfetm(pending);
        var scheduledId = matches.saved.getFirst().getId();

        // Same fixture as the published pair of the FEAT-00075 reference actas: same equipos ids,
        // jornada and group, published with games. The reference files carry different id_partido
        // values for two different fixtures; the published acta is aligned to the stored fixture id
        // so the FEAT-00085 identity guard lets the upgrade through (a mismatch is a conflict).
        Acta published = withMatchId(withTeams(acta("acta_rfetm_2026_published.json"),
                pending.acta().teams()), pending.acta().matchId());
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", published, runContext));

        assertEquals(1, matches.saved.size());
        Match upgraded = matches.saved.getFirst();
        assertEquals(scheduledId, upgraded.getId());
        assertEquals(MatchStatus.PLAYED, upgraded.getStatus());
        assertNotNull(upgraded.getWinnerTeam());
        assertEquals(7, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UPGRADED_TO_PLAYED));
    }

    @Test
    void rfetmRescheduleUpdatesChangedScheduleAndKeepsStoredValuesOnNull() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        Acta pending = acta("acta_rfetm_2026_unpublished.json");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", pending, runContext));
        var scheduledId = matches.saved.getFirst().getId();
        LocalDate storedDate = pending.date();

        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                withDate(pending, storedDate.plusDays(7)), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.RESCHEDULED));
        Match rescheduled = matches.saved.getFirst();
        assertEquals(scheduledId, rescheduled.getId());
        assertEquals(storedDate.plusDays(7), rescheduled.getDateTime().toLocalDate());
        assertEquals(MatchStatus.SCHEDULED, rescheduled.getStatus());

        // A later pending acta without a date keeps the stored date.
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                withDate(pending, null), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UNCHANGED));
        assertEquals(storedDate.plusDays(7), matches.saved.getFirst().getDateTime().toLocalDate());
    }

    @Test
    void rfetmReimportIsIdempotentForPendingAndPlayed() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        MatchReportContext context = rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext);
        runRfetm(context);
        runRfetm(context);

        assertEquals(1, matches.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.SCHEDULED_CREATED));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UNCHANGED));

        ImportRunContext playedRun = new ImportRunContext(ImportSource.RFETM, "2023-2024");
        MatchReportContext played = rfetmContext("2023-2024", "super-divisio", "1", "masculino",
                acta("acta_singles.json"), playedRun);
        runRfetm(played);
        int gamesAfterFirst = games.saved.size();
        runRfetm(played);

        // Two fixtures live in this store: the pending one and the played one.
        assertEquals(2, matches.saved.size());
        assertEquals(gamesAfterFirst, games.saved.size());
        assertEquals(1, count(playedRun, MatchLifecycleOutcome.PLAYED_CREATED));
        assertEquals(1, count(playedRun, MatchLifecycleOutcome.PLAYED_KEPT));
    }

    @Test
    void rfetmPendingAfterPlayedIsReportedAsRegressionAndNeverDowngrades() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2023-2024");
        Acta played = acta("acta_singles.json");
        runRfetm(rfetmContext("2023-2024", "super-divisio", "1", "masculino", played, runContext));

        runRfetm(rfetmContext("2023-2024", "super-divisio", "1", "masculino",
                withPublished(played, false), runContext));

        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertEquals(6, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.REGRESSION_REPORTED));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertEquals("acta_publicada is false", runContext.reportedMatchIssues().getFirst().message());
    }

    @Test
    void rfetmDecidedZeroZeroAndLegacyEmptyActasBecomeScheduled() {
        ImportRunContext decidedRun = new ImportRunContext(ImportSource.RFETM, "2025-2026");
        Acta decided = acta("acta_rfetm_2025_decided_0_0.json");
        runRfetm(rfetmContext("2025-2026", "divisio-honor", "5", "femenino", decided, decidedRun,
                RfetmClubKey.ofName("2025-2026", "divisio-honor-femenino", decided.teams().home().name()),
                RfetmClubKey.ofName("2025-2026", "divisio-honor-femenino", decided.teams().away().name())));

        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertNull(matches.saved.getFirst().getWinnerTeam());
        assertNull(matches.saved.getFirst().getHomeGamesWon());
        assertTrue(games.saved.isEmpty());

        ImportRunContext legacyRun = new ImportRunContext(ImportSource.RFETM, "2024-2025");
        Acta legacy = acta("acta_rfetm_legacy_empty.json");
        runRfetm(rfetmContext("2024-2025", "temporada-2024-2025", "3", "masculino", legacy, legacyRun));

        assertEquals(2, matches.saved.size());
        Match legacyMatch = matches.saved.getLast();
        assertEquals(MatchStatus.SCHEDULED, legacyMatch.getStatus());
        assertNull(legacyMatch.getWinnerTeam(), "the placeholder 6-0 must never be read");
        assertEquals(1, count(legacyRun, MatchLifecycleOutcome.SCHEDULED_CREATED));
    }

    @Test
    void rfetmPartialActaKeepsScheduledAndReports() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2023-2024");
        Acta partial = actaSinglesWithUnfinishedLastGame();

        runRfetm(rfetmContext("2023-2024", "super-divisio", "1", "masculino", partial, runContext));

        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertTrue(games.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.PARTIAL_REPORTED));
        assertEquals(1, runContext.reportedMatchIssues().size());
    }

    @Test
    void rfetmInvalidActaSchedulesNewFixtureAndNeverTouchesPlayedOne() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        MatchReportContext pending = rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext);
        Acta publishedButInvalid = withPublished(pending.acta(), true);

        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                publishedButInvalid, runContext));

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertTrue(lineups.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.INVALID_REPORTED));

        ImportRunContext playedRun = new ImportRunContext(ImportSource.RFETM, "2023-2024");
        Acta played = acta("acta_singles.json");
        runRfetm(rfetmContext("2023-2024", "super-divisio", "1", "masculino", played, playedRun));
        runRfetm(rfetmContext("2023-2024", "super-divisio", "1", "masculino",
                withGames(withPublished(played, true), List.of()), playedRun));

        assertEquals(MatchStatus.PLAYED, matches.saved.getLast().getStatus());
        assertEquals(6, games.saved.size());
        assertEquals(1, count(playedRun, MatchLifecycleOutcome.INVALID_REPORTED));
    }

    @Test
    void rfetmDoublesGameWithNoNamedPlayersStoresNoDoublesPair() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2023-2024");
        Acta doubles = actaEmptyDoublesHomeSide(acta("acta_doubles.json"));

        runRfetm(rfetmContext("2023-2024", "divisio-honor", "1", "femenino", doubles, runContext,
                RfetmClubKey.ofFederationId("16207", null), RfetmClubKey.ofFederationId("2017543", null)));

        var doublesGame = games.saved.stream().filter(g -> "DOUBLES".equals(g.getType())).findFirst();
        assertTrue(doublesGame.isPresent());
        assertTrue(doublesPairs.saved.stream().noneMatch(p -> "HOME".equals(p.getSide())));
        assertTrue(doublesPairs.saved.stream().anyMatch(p -> "AWAY".equals(p.getSide())));
    }

    @Test
    void rfetmIdPartidoIsStoredOnCreateAndThePublishedHeaderIsWrittenOnUpgrade() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        Acta unpublished = acta("acta_rfetm_2026_unpublished.json");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", unpublished, runContext));

        Match created = matches.saved.getFirst();
        assertEquals(unpublished.matchId(), created.getSourceFixtureId());
        assertEquals(created.getId(),
                matches.findBySourceFixtureId(ImportSource.RFETM, unpublished.matchId()).orElseThrow().getId());

        // The published version of the same fixture carries the same id_partido; a different one
        // would be a FEAT-00085 conflict instead (see the mismatch test below).
        Acta published = withMatchId(withTeams(acta("acta_rfetm_2026_published.json"),
                unpublished.teams()), unpublished.matchId());
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", published, runContext));

        assertEquals(1, matches.saved.size());
        Match upgraded = matches.saved.getFirst();
        assertEquals(created.getId(), upgraded.getId());
        assertEquals(MatchStatus.PLAYED, upgraded.getStatus());
        assertEquals(unpublished.matchId(), upgraded.getSourceFixtureId());
        assertTrue(matches.findBySourceFixtureId(ImportSource.RFETM, published.matchId()).isPresent());
    }

    @Test
    void rfetmPublishedActaWithADifferentIdPartidoConflictsAndNeverOverwritesTheStoredOne() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        Acta unpublished = acta("acta_rfetm_2026_unpublished.json");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", unpublished, runContext));
        Match created = matches.saved.getFirst();

        // Same natural key, different id_partido (FEAT-00085): the stored fixture wins.
        Acta mismatched = withTeams(acta("acta_rfetm_2026_published.json"), unpublished.teams());
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", mismatched, runContext));

        assertEquals(1, matches.saved.size());
        Match stored = matches.saved.getFirst();
        assertEquals(created.getId(), stored.getId());
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus(), "the conflicting upgrade is not applied");
        assertEquals(unpublished.matchId(), stored.getSourceFixtureId());
        assertTrue(games.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertEquals(1, runContext.reportedMatchIssues().size());
        String reason = runContext.reportedMatchIssues().getFirst().message();
        assertTrue(reason.contains(unpublished.matchId()), reason);
        assertTrue(reason.contains(mismatched.matchId()), reason);
    }

    @Test
    void rfetmActaWithoutPayloadJornadaStoresDayFolderRoundAndReportsTheFallback() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "4", "femenino",
                withRound(acta("acta_rfetm_2026_unpublished.json"), null), runContext));

        assertEquals(1, matches.saved.size());
        assertEquals(4, matches.saved.getFirst().getRound());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.SCHEDULED_CREATED));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertEquals("RfetmMatchImportProcessor", runContext.reportedMatchIssues().getFirst().processor());
        assertTrue(runContext.reportedMatchIssues().getFirst().message().contains("day folder 4"),
                runContext.reportedMatchIssues().getFirst().message());
    }

    @Test
    void rfetmActaWithPayloadJornadaRecordsNoFallbackWarning() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "4", "femenino",
                acta("acta_rfetm_2026_unpublished.json"), runContext));

        assertEquals(1, matches.saved.getFirst().getRound());
        assertTrue(runContext.reportedMatchIssues().isEmpty());
    }

    @Test
    void rfetmJornadaDriftWithTheSameIdPartidoConflictsInsteadOfDuplicating() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        Acta pending = acta("acta_rfetm_2026_unpublished.json");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", pending, runContext));
        Match created = matches.saved.getFirst();

        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino",
                withRound(pending, pending.round() + 1), runContext));

        assertEquals(1, matches.saved.size());
        Match stored = matches.saved.getFirst();
        assertEquals(created.getId(), stored.getId());
        assertEquals(pending.round(), stored.getRound());
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertTrue(runContext.reportedMatchIssues().getFirst().message().contains(pending.matchId()));
    }

    @Test
    void rfetmJornadaDriftWithAPlayedSecondVersionDoesNotUpgradeTheStoredMatch() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2026-2027");
        Acta pending = acta("acta_rfetm_2026_unpublished.json");
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", pending, runContext));

        Acta playedElsewhere = withMatchId(withTeams(withRound(
                acta("acta_rfetm_2026_published.json"), pending.round() + 1), pending.teams()),
                pending.matchId());
        runRfetm(rfetmContext("2026-2027", "divisio-honor", "1", "femenino", playedElsewhere, runContext));

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertTrue(games.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
    }

    @Test
    void rfetmLegacyActaStoresNoFixtureId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.RFETM, "2024-2025");
        Acta legacy = acta("acta_rfetm_legacy_empty.json");
        runRfetm(rfetmContext("2024-2025", "temporada-2024-2025", "3", "masculino", legacy, runContext));

        assertNull(matches.saved.getFirst().getSourceFixtureId());
        assertNull(legacy.matchId());
    }

    // --- FCTT --------------------------------------------------------------------------------

    @Test
    void fcttPlaceholderSixZeroBecomesScheduledIgnoringTheFakeResult() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2025-2026");
        runFctt(fcttContext("2025-2026", "Tercera nacional", "G3",
                acta("acta_fctt_2025_placeholder_6_0.json"), runContext));

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam(), "the placeholder 6-0 winner must never be read");
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertTrue(games.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.SCHEDULED_CREATED));
    }

    @Test
    void fcttPublishedActaUpgradesStoredScheduledMatchKeepingItsId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1",
                acta("acta_fctt_unpublished.json"), runContext));
        var scheduledId = matches.saved.getFirst().getId();

        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1",
                acta("acta_fctt_2026_published.json"), runContext));

        assertEquals(1, matches.saved.size());
        Match upgraded = matches.saved.getFirst();
        assertEquals(scheduledId, upgraded.getId());
        assertEquals(MatchStatus.PLAYED, upgraded.getStatus());
        assertNotNull(upgraded.getWinnerTeam());
        assertEquals(6, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UPGRADED_TO_PLAYED));
    }

    @Test
    void fcttRescheduleThenIdempotentReimportAndRegression() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");
        Acta pending = acta("acta_fctt_unpublished.json");
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", pending, runContext));

        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1",
                withDate(pending, pending.date().plusDays(7)), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.RESCHEDULED));

        // Re-importing the same moved-date acta changes nothing.
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1",
                withDate(pending, pending.date().plusDays(7)), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UNCHANGED));
        assertEquals(1, matches.saved.size());

        // Upgrade, then a pending acta for the same fixture: PLAYED must survive.
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1",
                acta("acta_fctt_2026_published.json"), runContext));
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", pending, runContext));

        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertEquals(6, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.REGRESSION_REPORTED));
    }

    @Test
    void fcttInvalidActaCreatesScheduledAndKeepsPlayed() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");
        Acta publishedNoGames = withGames(acta("acta_fctt_2026_published.json"), List.of());

        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", publishedNoGames, runContext));

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertTrue(lineups.saved.isEmpty());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.INVALID_REPORTED));
    }

    @Test
    void fcttDoublesGameWithNoNamedPlayersStoresNoDoublesPair() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2023-2024");
        Acta doubles = actaEmptyDoublesHomeSide(acta("acta_doubles.json"));

        runFctt(fcttContext("2023-2024", "Tercera nacional", "G3", doubles, runContext));

        assertTrue(games.saved.stream().anyMatch(g -> "DOUBLES".equals(g.getType())));
        assertTrue(doublesPairs.saved.stream().noneMatch(p -> "HOME".equals(p.getSide())));
        assertTrue(doublesPairs.saved.stream().anyMatch(p -> "AWAY".equals(p.getSide())));
    }

    @Test
    void fcttUnpublishedAndPublishedVersionsShareOneMatchAndOneFixtureId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");
        Acta unpublished = acta("acta_fctt_unpublished.json");
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", unpublished, runContext));

        Match created = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, created.getStatus());
        assertEquals(unpublished.matchId(), created.getSourceFixtureId());

        Acta published = acta("acta_fctt_2026_published.json");
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", published, runContext));

        // Same fixture and same id_partido in both files (G15): one match, one fixture id.
        assertEquals(unpublished.matchId(), published.matchId());
        assertEquals(1, matches.saved.size());
        Match upgraded = matches.saved.getFirst();
        assertEquals(created.getId(), upgraded.getId());
        assertEquals(MatchStatus.PLAYED, upgraded.getStatus());
        assertEquals(unpublished.matchId(), upgraded.getSourceFixtureId());
        assertEquals(upgraded.getId(),
                matches.findBySourceFixtureId(ImportSource.FCTT, published.matchId()).orElseThrow().getId());
    }

    @Test
    void fcttJornadaDriftWithTheSameIdPartidoConflictsInsteadOfDuplicating() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.FCTT, "2026-2027");
        Acta pending = acta("acta_fctt_unpublished.json");
        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", pending, runContext));
        Match created = matches.saved.getFirst();

        runFctt(fcttContext("2026-2027", "tercera-nacional", "G1", withRound(pending, 2), runContext));

        assertEquals(1, matches.saved.size());
        Match stored = matches.saved.getFirst();
        assertEquals(created.getId(), stored.getId());
        assertEquals(pending.round(), stored.getRound());
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertTrue(runContext.reportedMatchIssues().getFirst().message().contains(pending.matchId()));
    }

    // --- BCNESA --------------------------------------------------------------------------------

    @Test
    void bcnesaPlaceholderFourFourBecomesScheduledAndRegistersItsTeams() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        Acta pending = acta("acta_bcnesa_2026_placeholder_4_4.json");

        runBcnesa(bcnesaContext("2026-2027", "1a Comarcal", "G1", "1a Fase",
                pending, pending.games(), runContext));

        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam(), "the placeholder 4-4 must never be read");
        assertNull(match.getHomeGamesWon());
        assertTrue(games.saved.isEmpty());
        assertTrue(playerSeasons.byId.isEmpty(), "a no-games fixture registers no players");
        assertEquals(2, teams.byId.size(), "both equipos are registered as teams");
        assertEquals(1, count(runContext, MatchLifecycleOutcome.SCHEDULED_CREATED));
    }

    @Test
    void bcnesaPublishedFixtureUpgradesStoredScheduledMatchKeepingItsId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2020-2021");
        Acta matchday = acta("acta_matchday.json");
        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                withPublished(matchday, false), firstFixtureGames(matchday), runContext));
        var scheduledId = matches.saved.getFirst().getId();

        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                matchday, firstFixtureGames(matchday), runContext));

        assertEquals(1, matches.saved.size());
        Match upgraded = matches.saved.getFirst();
        assertEquals(scheduledId, upgraded.getId());
        assertEquals(MatchStatus.PLAYED, upgraded.getStatus());
        assertEquals(2, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UPGRADED_TO_PLAYED));
    }

    @Test
    void bcnesaRescheduleIdempotentReimportAndRegression() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2020-2021");
        Acta matchday = acta("acta_matchday.json");
        Acta pending = withPublished(matchday, false);
        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                pending, firstFixtureGames(matchday), runContext));

        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                withDate(pending, pending.date().plusDays(7)), firstFixtureGames(matchday), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.RESCHEDULED));

        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                withDate(pending, pending.date().plusDays(7)), firstFixtureGames(matchday), runContext));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UNCHANGED));
        assertEquals(1, matches.saved.size());

        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                matchday, firstFixtureGames(matchday), runContext));
        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                pending, firstFixtureGames(matchday), runContext));

        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertEquals(2, games.saved.size());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.REGRESSION_REPORTED));
    }

    @Test
    void bcnesaDoublesGameWithNoNamedPlayersStoresNoDoublesPair() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2020-2021");
        Acta doubles = actaEmptyDoublesHomeSide(acta("acta_doubles_matchday.json"));

        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase", doubles, doubles.games(),
                runContext));

        assertTrue(games.saved.stream().anyMatch(g -> "DOUBLES".equals(g.getType())));
        assertTrue(doublesPairs.saved.stream().noneMatch(p -> "HOME".equals(p.getSide())));
        assertTrue(doublesPairs.saved.stream().anyMatch(p -> "AWAY".equals(p.getSide())));
    }

    @Test
    void bcnesaPendingActaStoresIdPartidoAndALaterSplitFixtureNeverBorrowsIt() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        Acta pending = acta("acta_bcnesa_2026_unpublished.json");
        runBcnesa(bcnesaContext("2026-2027", "1a Comarcal", "G1", "1a Fase",
                pending, pending.games(), runContext));

        Match created = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, created.getStatus());
        assertNotNull(pending.matchId());
        assertEquals(pending.matchId(), created.getSourceFixtureId());
        assertEquals(created.getId(),
                matches.findBySourceFixtureId(ImportSource.BCNESA, pending.matchId()).orElseThrow().getId());
    }

    @Test
    void bcnesaSplitFixtureBeyondIndexZeroIsCreatedWithoutTheFileIdPartido() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        Acta pending = acta("acta_bcnesa_2026_unpublished.json");
        BcnesaMatchReportContext secondFixture = new BcnesaMatchReportContext("2026-2027", "1a Comarcal",
                "G1", "1a Fase", pending.round(), 1, pending.teams().home().name(),
                pending.teams().away().name(), ANY_FILE, pending, pending.games(), runContext);

        runBcnesa(secondFixture);

        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertNull(matches.saved.getFirst().getSourceFixtureId(),
                "only fixture 0 owns the file's id_partido");
        assertTrue(matches.findBySourceFixtureId(ImportSource.BCNESA, pending.matchId()).isEmpty());

        // A fixture without a fixture id skips the guard's lookup (FEAT-00085): re-import is plain.
        runBcnesa(secondFixture);
        assertEquals(1, matches.saved.size());
        assertEquals(0, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertEquals(1, count(runContext, MatchLifecycleOutcome.UNCHANGED));
    }

    @Test
    void bcnesaJornadaDriftWithTheSameIdPartidoConflictsInsteadOfDuplicating() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2026-2027");
        Acta pending = acta("acta_bcnesa_2026_unpublished.json");
        runBcnesa(bcnesaContext("2026-2027", "1a Comarcal", "G1", "1a Fase",
                pending, pending.games(), runContext));
        Match created = matches.saved.getFirst();

        runBcnesa(bcnesaContext("2026-2027", "1a Comarcal", "G1", "1a Fase",
                withRound(pending, pending.round() + 1), pending.games(), runContext));

        assertEquals(1, matches.saved.size());
        Match stored = matches.saved.getFirst();
        assertEquals(created.getId(), stored.getId());
        assertEquals(pending.round(), stored.getRound());
        assertEquals(MatchStatus.SCHEDULED, stored.getStatus());
        assertEquals(1, count(runContext, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT));
        assertTrue(runContext.reportedMatchIssues().getFirst().message().contains(pending.matchId()));
    }

    @Test
    void bcnesaLegacyActaStoresNoFixtureId() {
        ImportRunContext runContext = new ImportRunContext(ImportSource.BCNESA, "2020-2021");
        Acta matchday = acta("acta_matchday.json");
        runBcnesa(bcnesaContext("2020-2021", "Preferent", "G1", "1a Fase",
                withPublished(matchday, false), firstFixtureGames(matchday), runContext));

        assertNull(matchday.matchId());
        assertNull(matches.saved.getFirst().getSourceFixtureId());
    }

    // --- helpers -------------------------------------------------------------------------------

    private static int count(ImportRunContext runContext, MatchLifecycleOutcome outcome) {
        return runContext.matchOutcomeCounts().getOrDefault(outcome, 0);
    }

    private void runRfetm(MatchReportContext context) {
        rfetmProcessors.forEach(processor -> processor.process(context));
    }

    private void runFctt(FcttMatchReportContext context) {
        fcttProcessors.forEach(processor -> processor.process(context));
    }

    private void runBcnesa(BcnesaMatchReportContext context) {
        bcnesaProcessors.forEach(processor -> processor.process(context));
    }

    private static MatchReportContext rfetmContext(String season, String league, String day, String sex,
                                                   Acta acta, ImportRunContext runContext) {
        return rfetmContext(season, league, day, sex, acta, runContext,
                RfetmClubKey.ofFederationId(acta.teams().home().rfetmId(), null),
                RfetmClubKey.ofFederationId(acta.teams().away().rfetmId(), null));
    }

    private static MatchReportContext rfetmContext(String season, String league, String day, String sex,
                                                   Acta acta, ImportRunContext runContext,
                                                   RfetmClubKey home, RfetmClubKey away) {
        return new MatchReportContext(season, league, day, sex, home, away, ANY_FILE, acta, runContext);
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

    private static List<ActaGame> firstFixtureGames(Acta matchday) {
        return matchday.games().subList(0, 2);
    }

    private static Acta acta(String name) {
        return new ActaParser().parse(fixture(name));
    }

    private static Path fixture(String name) {
        URL resource = MatchLifecycleImportProcessorsTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Acta withPublished(Acta acta, boolean published) {
        return copy(acta, b -> b.published = published ? Boolean.TRUE : Boolean.FALSE);
    }

    private static Acta withDate(Acta acta, LocalDate date) {
        return copy(acta, b -> b.date = date);
    }

    private static Acta withMatchId(Acta acta, String matchId) {
        return copy(acta, b -> b.matchId = matchId);
    }

    private static Acta withRound(Acta acta, Integer round) {
        return copy(acta, b -> b.round = round);
    }

    private static Acta withTeams(Acta acta, org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeams teams) {
        return copy(acta, b -> b.teams = teams);
    }

    private static Acta withGames(Acta acta, List<ActaGame> games) {
        return copy(acta, b -> b.games = games);
    }

    /**
     * The doubles game of the given acta with its home side carrying {@code jugadores: []}.
     */
    private static Acta actaEmptyDoublesHomeSide(Acta acta) {
        List<ActaGame> games = acta.games().stream()
                .map(game -> game.isDoubles() ? emptyDoublesHomeSide(game) : game)
                .toList();
        return withGames(acta, games);
    }

    private static ActaGame emptyDoublesHomeSide(ActaGame game) {
        ActaParticipant home = game.home();
        ActaParticipant emptied = new ActaParticipant(home.letter(), home.name(), home.license(),
                home.rfetmId(), home.ranking(), List.of());
        return new ActaGame(game.number(), game.type(), game.crossover(), emptied, game.away(),
                game.sets(), game.setsWon(), game.winner(), game.cumulativeScore(), game.notPlayed(),
                game.reason());
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

    /**
     * Mutates one {@link Acta} component at a time without touching the record constructor's
     * twenty arguments in every helper.
     */
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
