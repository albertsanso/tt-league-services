package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00086: snapshot reconciliation reports stored SCHEDULED matches absent from a snapshot,
 * matched by fixture id or natural key, honouring the FCTT sliding-window rule, and never writes.
 */
class SnapshotReconcilerTest {

    private static final ImportSource FCTT = ImportSource.FCTT;
    private static final ImportSource RFETM = ImportSource.RFETM;
    private static final Season SEASON = Season.of(2026);
    private static final Season OTHER_SEASON = Season.of(2025);
    private static final String COMPETITION = "tercera-nacional";
    private static final String PHASE = "1a Fase";

    @Test
    void vanishedFixtureIsReportedOnce() {
        InMemoryRepositories.Matches matches = newMatches();
        Match vanished = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "X", "Home X", "Away X");
        Match seenMatch = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "Y", "Home Y", "Away Y");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "Y",
                seenMatch.getHomeTeam().getId(), seenMatch.getAwayTeam().getId());

        List<ImportExecutionIssue> issues =
                new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures());

        assertEquals(1, issues.size());
        ImportExecutionIssue issue = issues.getFirst();
        assertEquals("SnapshotReconciliation", issue.processor());
        assertEquals("match " + vanished.getId(), issue.location());
        assertTrue(issue.message().contains("round 1"), issue.message());
        assertTrue(issue.message().contains("Home X"), issue.message());
        assertTrue(issue.message().contains("Away X"), issue.message());
        assertTrue(issue.message().contains("id_partido X"), issue.message());
        assertTrue(issue.message().contains("kept, not deleted"), issue.message());
    }

    @Test
    void seenByFixtureIdWithDriftedNaturalKeyIsNotFlagged() {
        InMemoryRepositories.Matches matches = newMatches();
        storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "Z", "Home Z", "Away Z");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "Z", UUID.randomUUID(), UUID.randomUUID());

        assertTrue(new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures()).isEmpty());
    }

    @Test
    void seenByNaturalKeyWithNullStoredFixtureIdIsNotFlagged() {
        InMemoryRepositories.Matches matches = newMatches();
        Match legacy = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, null, "Home L", "Away L");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, null,
                legacy.getHomeTeam().getId(), legacy.getAwayTeam().getId());

        assertTrue(new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures()).isEmpty());
    }

    @Test
    void fcttWindowFlagsMissingInnerRoundButNotRoundsBeyondTheSnapshot() {
        InMemoryRepositories.Matches matches = newMatches();
        Match round1 = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "F1", "H1", "A1");
        Match round2 = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 2, "F2", "H2", "A2");
        Match vanishedRound2 = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 2, "F2X", "HX", "AX");
        storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 3, "F3", "H3", "A3");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "F1",
                round1.getHomeTeam().getId(), round1.getAwayTeam().getId());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 2, "F2",
                round2.getHomeTeam().getId(), round2.getAwayTeam().getId());

        List<ImportExecutionIssue> issues =
                new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures());

        assertEquals(1, issues.size(), "round 3 is beyond the window; round 2 vanished fixture is within it");
        assertEquals("match " + vanishedRound2.getId(), issues.getFirst().location());
        assertTrue(issues.getFirst().message().contains("round 2"), issues.getFirst().message());
        assertTrue(issues.getFirst().message().contains("id_partido F2X"), issues.getFirst().message());
    }

    @Test
    void vanishedScopeUsesOverallHighestRoundAsTheWindow() {
        InMemoryRepositories.Matches matches = newMatches();
        Match group2Round1 = storeScheduled(matches, FCTT, SEASON, COMPETITION, 2, PHASE, 1, "GB1", "HB1", "AB1");
        storeScheduled(matches, FCTT, SEASON, COMPETITION, 2, PHASE, 5, "GB2", "HB2", "AB2");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "GA1", UUID.randomUUID(), UUID.randomUUID());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 2, "GA2", UUID.randomUUID(), UUID.randomUUID());

        List<ImportExecutionIssue> issues =
                new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures());

        assertEquals(1, issues.size(), "round 1 is within the overall window; round 5 is beyond it");
        assertEquals("match " + group2Round1.getId(), issues.getFirst().location());
    }

    @Test
    void playedOtherSourceAndOtherSeasonAreNeverFlagged() {
        InMemoryRepositories.Matches matches = newMatches();
        Match seen = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "SEEN1", "HS", "AS");
        storePlayed(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "PLAYED1", "HP", "AP");
        storeScheduled(matches, RFETM, SEASON, COMPETITION, 1, PHASE, 1, "OTHERSRC", "HO", "AO");
        storeScheduled(matches, FCTT, OTHER_SEASON, COMPETITION, 1, PHASE, 1, "OTHERSEASON", "HN", "AN");

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "SEEN1",
                seen.getHomeTeam().getId(), seen.getAwayTeam().getId());

        assertTrue(new SnapshotReconciler(matches).reconcile(FCTT, SEASON, runContext.snapshotFixtures()).isEmpty());
    }

    @Test
    void emptySeenReturnsEmptyListWithoutReadingTheRepository() {
        InMemoryRepositories.Matches matches = newMatches();
        storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "ANY", "H", "A");
        RecordingMatchRepository recording = new RecordingMatchRepository(matches);

        List<ImportExecutionIssue> issues = new SnapshotReconciler(recording)
                .reconcile(FCTT, SEASON, new SnapshotFixtures(Set.of(), Set.of(), Map.of()));

        assertTrue(issues.isEmpty());
        assertEquals(0, recording.findCalls);
    }

    @Test
    void reconciliationNeverWrites() {
        InMemoryRepositories.Matches matches = newMatches();
        storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "X", "HX", "AX");
        Match seenMatch = storeScheduled(matches, FCTT, SEASON, COMPETITION, 1, PHASE, 1, "Y", "HY", "AY");
        RecordingMatchRepository recording = new RecordingMatchRepository(matches);
        List<UUID> idsBefore = matches.saved.stream().map(Match::getId).toList();
        List<MatchStatus> statusesBefore = matches.saved.stream().map(Match::getStatus).toList();

        ImportRunContext runContext = new ImportRunContext(FCTT, SEASON.toString());
        runContext.recordSnapshotFixture(COMPETITION, 1, PHASE, 1, "Y",
                seenMatch.getHomeTeam().getId(), seenMatch.getAwayTeam().getId());

        List<ImportExecutionIssue> issues =
                new SnapshotReconciler(recording).reconcile(FCTT, SEASON, runContext.snapshotFixtures());

        assertEquals(1, issues.size());
        assertEquals(1, recording.findCalls);
        assertEquals(0, recording.saveCalls);
        assertEquals(0, recording.replaceCalls);
        assertEquals(0, recording.updateScheduleCalls);
        assertEquals(idsBefore, matches.saved.stream().map(Match::getId).toList());
        assertEquals(statusesBefore, matches.saved.stream().map(Match::getStatus).toList());
    }

    // --- fixtures ----------------------------------------------------------------------------

    /** A read-only in-memory store; the reconciler never writes, so the child stores stay unwired. */
    private static InMemoryRepositories.Matches newMatches() {
        return new InMemoryRepositories.Matches(null, null, null, null);
    }

    private static Match storeScheduled(InMemoryRepositories.Matches matches, ImportSource source, Season season,
                                        String competition, Integer group, String phase, int round,
                                        String fixtureId, String homeName, String awayName) {
        return store(matches, source, season, competition, group, phase, round, fixtureId, homeName, awayName,
                MatchStatus.SCHEDULED);
    }

    private static Match storePlayed(InMemoryRepositories.Matches matches, ImportSource source, Season season,
                                     String competition, Integer group, String phase, int round,
                                     String fixtureId, String homeName, String awayName) {
        return store(matches, source, season, competition, group, phase, round, fixtureId, homeName, awayName,
                MatchStatus.PLAYED);
    }

    private static Match store(InMemoryRepositories.Matches matches, ImportSource source, Season season,
                               String competition, Integer group, String phase, int round,
                               String fixtureId, String homeName, String awayName, MatchStatus status) {
        Team home = Team.createExisting(UUID.randomUUID(), source, homeName, season, null);
        Team away = Team.createExisting(UUID.randomUUID(), source, awayName, season, null);
        Match.MatchBuilder builder = Match.builder().id(UUID.randomUUID()).source(source)
                .sourceFixtureId(fixtureId).competition(competition).season(season)
                .groupNumber(group).round(round).phase(phase).homeTeam(home).awayTeam(away);
        if (status == MatchStatus.PLAYED) {
            builder.homeGamesWon(5).awayGamesWon(2).winnerTeam(home);
        }
        Match match = builder.status(status).createExisting();
        matches.saveMatch(match);
        return match;
    }

    /** Delegates to an in-memory store while counting the reads and writes reconciliation performs. */
    private static final class RecordingMatchRepository implements MatchRepository {
        private final InMemoryRepositories.Matches delegate;
        private int findCalls;
        private int saveCalls;
        private int replaceCalls;
        private int updateScheduleCalls;

        private RecordingMatchRepository(InMemoryRepositories.Matches delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Match> findMatchesBySourceSeasonAndStatus(ImportSource source, Season season,
                                                              MatchStatus status) {
            findCalls++;
            return delegate.findMatchesBySourceSeasonAndStatus(source, season, status);
        }

        @Override
        public void saveMatch(Match match) {
            saveCalls++;
            delegate.saveMatch(match);
        }

        @Override
        public void replaceMatchContent(MatchContent content) {
            replaceCalls++;
            delegate.replaceMatchContent(content);
        }

        @Override
        public void updateSchedule(UUID matchId, MatchSchedule schedule) {
            updateScheduleCalls++;
            delegate.updateSchedule(matchId, schedule);
        }

        @Override
        public void recordSourceChecksum(UUID matchId, String sourceChecksum) {
            delegate.recordSourceChecksum(matchId, sourceChecksum);
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
            return delegate.findMatchByNaturalKey(competition, season, groupNumber, round, phase,
                    homeTeamId, awayTeamId);
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
        public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                Collection<UUID> teamIds, ImportSource source, Season season, String competition) {
            return delegate.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(teamIds, source, season,
                    competition);
        }

        @Override
        public List<RoundProgress> findRoundProgress(ImportSource source, Season season) {
            return delegate.findRoundProgress(source, season);
        }

        @Override
        public List<org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount>
                findRoundStatusCounts(ImportSource source, Season season) {
            return delegate.findRoundStatusCounts(source, season);
        }
    }
}
