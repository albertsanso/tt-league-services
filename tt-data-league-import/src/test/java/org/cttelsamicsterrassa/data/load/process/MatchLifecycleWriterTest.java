package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompleteness;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision-table tests for the shared {@link MatchLifecycleWriter} (FEAT-00081) against the
 * wired in-memory match repository.
 */
class MatchLifecycleWriterTest {

    private static final Season SEASON = Season.of(2026);
    private static final ZonedDateTime DATE_A = LocalDate.of(2026, 9, 27)
            .atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);
    private static final ZonedDateTime DATE_B = LocalDate.of(2026, 10, 4)
            .atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);

    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private MatchLifecycleWriter writer;
    private Team homeTeam;
    private Team awayTeam;

    @BeforeEach
    void setUp() {
        lineups = new InMemoryRepositories.Lineups();
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();
        matches = new InMemoryRepositories.Matches(lineups, games, setScores, doublesPairs);
        writer = new MatchLifecycleWriter(matches, lineups, games, setScores, doublesPairs);
        homeTeam = Team.createNew(ImportSource.RFETM, "HOME", SEASON, null);
        awayTeam = Team.createNew(ImportSource.RFETM, "AWAY", SEASON, null);
    }

    // --- PLAYED --------------------------------------------------------------------------

    @Test
    void playedWithoutExistingCreatesFullContent() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(classification(ActaCompleteness.PLAYED),
                Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.PLAYED_CREATED, outcome);
        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.PLAYED, matches.saved.getFirst().getStatus());
        assertEquals(1, lineups.saved.size());
        assertFalse(source.scheduledBuilt);
    }

    @Test
    void playedWithScheduledExistingUpgradesInPlace() {
        UUID storedId = storeScheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "other", "other venue", "other referee");

        MatchLifecycleOutcome outcome = writer.apply(classification(ActaCompleteness.PLAYED),
                matches.findMatchById(storedId), source);

        assertEquals(MatchLifecycleOutcome.UPGRADED_TO_PLAYED, outcome);
        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(storedId, match.getId());
        assertEquals(MatchStatus.PLAYED, match.getStatus());
        assertEquals(1, lineups.saved.size());
    }

    @Test
    void playedWithPlayedExistingIsKeptWithoutBuilding() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");
        Match played = source.buildPlayedContent(UUID.randomUUID(), false).match();
        matches.saveMatch(played);
        source.playedBuilds.clear();

        MatchLifecycleOutcome outcome = writer.apply(classification(ActaCompleteness.PLAYED),
                Optional.of(played), source);

        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, outcome);
        assertTrue(source.playedBuilds.isEmpty(), "a played match is never rewritten");
    }

    // --- PENDING -------------------------------------------------------------------------

    @Test
    void pendingWithoutExistingCreatesScheduledHeaderOnly() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PENDING), Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.SCHEDULED_CREATED, outcome);
        assertEquals(1, matches.saved.size());
        Match match = matches.saved.getFirst();
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam());
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertTrue(lineups.saved.isEmpty());
        assertTrue(games.saved.isEmpty());
        assertTrue(source.playedBuilds.isEmpty());
    }

    @Test
    void pendingWithEqualScheduleIsUnchanged() {
        UUID storedId = storeScheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PENDING), matches.findMatchById(storedId), source);

        assertEquals(MatchLifecycleOutcome.UNCHANGED, outcome);
        assertEquals(storedId, matches.saved.getFirst().getId());
        assertEquals(DATE_A, matches.saved.getFirst().getDateTime());
    }

    @Test
    void pendingWithDifferentScheduleReschedulesKeepingTheId() {
        UUID storedId = storeScheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "new city", "new venue", "new referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PENDING), matches.findMatchById(storedId), source);

        assertEquals(MatchLifecycleOutcome.RESCHEDULED, outcome);
        Match match = matches.saved.getFirst();
        assertEquals(storedId, match.getId());
        assertEquals(DATE_B, match.getDateTime());
        assertEquals("new city", match.getCity());
        assertEquals("new venue", match.getVenue());
        assertEquals("new referee", match.getRefereeName());
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
    }

    @Test
    void pendingWithNullIncomingComponentsKeepsStoredValues() {
        UUID storedId = storeScheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(null, null, null, null);

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PENDING), matches.findMatchById(storedId), source);

        assertEquals(MatchLifecycleOutcome.UNCHANGED, outcome);
        Match match = matches.saved.getFirst();
        assertEquals(DATE_A, match.getDateTime());
        assertEquals("city", match.getCity());
        assertEquals("venue", match.getVenue());
        assertEquals("referee", match.getRefereeName());
    }

    @Test
    void pendingWithPlayedExistingReportsRegressionWithoutWriting() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");
        Match played = source.buildPlayedContent(UUID.randomUUID(), false).match();
        matches.saveMatch(played);

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PENDING), Optional.of(played), source);

        assertEquals(MatchLifecycleOutcome.REGRESSION_REPORTED, outcome);
        assertSame(played, matches.saved.getFirst());
        assertTrue(outcome.isReportable());
    }

    // --- PARTIAL and INVALID ---------------------------------------------------------------

    @Test
    void partialWithoutExistingCreatesScheduledAndReports() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PARTIAL), Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.PARTIAL_REPORTED, outcome);
        assertTrue(outcome.isReportable());
        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
        assertTrue(source.playedBuilds.isEmpty());
    }

    @Test
    void invalidWithoutExistingCreatesScheduledAndReports() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.INVALID), Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED, outcome);
        assertEquals(1, matches.saved.size());
        assertEquals(MatchStatus.SCHEDULED, matches.saved.getFirst().getStatus());
    }

    @Test
    void partialWithScheduledExistingReschedulesAndReports() {
        UUID storedId = storeScheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PARTIAL), matches.findMatchById(storedId), source);

        assertEquals(MatchLifecycleOutcome.PARTIAL_REPORTED, outcome);
        assertEquals(DATE_B, matches.saved.getFirst().getDateTime());
    }

    @Test
    void partialWithPlayedExistingReportsRegression() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");
        Match played = source.buildPlayedContent(UUID.randomUUID(), false).match();
        matches.saveMatch(played);

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.PARTIAL), Optional.of(played), source);

        assertEquals(MatchLifecycleOutcome.REGRESSION_REPORTED, outcome);
    }

    @Test
    void invalidWithPlayedExistingReportsInvalid() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");
        Match played = source.buildPlayedContent(UUID.randomUUID(), false).match();
        matches.saveMatch(played);

        MatchLifecycleOutcome outcome = writer.apply(
                classification(ActaCompleteness.INVALID), Optional.of(played), source);

        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED, outcome);
    }

    // --- ImportRunContext recording --------------------------------------------------------

    @Test
    void runContextCountsOutcomesAndKeepsReportedIssues() {
        org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext runContext =
                new org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext(
                        ImportSource.RFETM, "2026-2027");

        runContext.recordMatchOutcome(MatchLifecycleOutcome.SCHEDULED_CREATED, "Processor",
                Path.of("a.json"), "acta_publicada is false");
        runContext.recordMatchOutcome(MatchLifecycleOutcome.INVALID_REPORTED, "Processor",
                Path.of("b.json"), "no games");

        assertEquals(1, runContext.matchOutcomeCounts().get(MatchLifecycleOutcome.SCHEDULED_CREATED));
        assertEquals(1, runContext.matchOutcomeCounts().get(MatchLifecycleOutcome.INVALID_REPORTED));
        assertEquals(1, runContext.reportedMatchIssues().size());
        assertEquals("Processor", runContext.reportedMatchIssues().getFirst().processor());
        assertEquals("no games", runContext.reportedMatchIssues().getFirst().message());
    }

    // --- helpers -----------------------------------------------------------------------------

    private static ActaClassification classification(ActaCompleteness completeness) {
        return new ActaClassification(completeness, "test " + completeness, false);
    }

    private UUID storeScheduled(ZonedDateTime date, String city, String venue, String referee) {
        UUID id = UUID.randomUUID();
        matches.saveMatch(scheduledBuilder(id, date, city, venue, referee).createNew());
        return id;
    }

    private Match.MatchBuilder scheduledBuilder(UUID id, ZonedDateTime date, String city,
                                                String venue, String referee) {
        return Match.builder()
                .id(id)
                .source(ImportSource.RFETM)
                .competition("divisio-honor-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .dateTime(date)
                .city(city)
                .venue(venue)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .refereeName(referee)
                .status(MatchStatus.SCHEDULED);
    }

    /**
     * Minimal source: the scheduled header carries the given schedule fields, and the played
     * content is a PLAYED header with a single empty lineup to keep MatchContent validation real.
     */
    private final class FakeSource implements MatchLifecycleSource {

        private final ZonedDateTime date;
        private final String city;
        private final String venue;
        private final String referee;
        private final List<UUID> playedBuilds = new java.util.ArrayList<>();
        private boolean scheduledBuilt;

        private FakeSource(ZonedDateTime date, String city, String venue, String referee) {
            this.date = date;
            this.city = city;
            this.venue = venue;
            this.referee = referee;
        }

        @Override
        public Match buildScheduledMatch(UUID id) {
            scheduledBuilt = true;
            return scheduledBuilder(id, date, city, venue, referee).createNew();
        }

        @Override
        public MatchContent buildPlayedContent(UUID id, boolean existing) {
            playedBuilds.add(id);
            Match.MatchBuilder builder = Match.builder()
                    .id(id)
                    .source(ImportSource.RFETM)
                    .competition("divisio-honor-masculino")
                    .season(SEASON)
                    .groupNumber(0)
                    .round(1)
                    .dateTime(date)
                    .city(city)
                    .venue(venue)
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .refereeName(referee)
                    .status(MatchStatus.PLAYED);
            Match match = existing ? builder.createExisting() : builder.createNew();
            org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup lineup =
                    org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup.builder()
                            .id(UUID.randomUUID())
                            .match(match)
                            .team(homeTeam)
                            .letter("A")
                            .position(1)
                            .player(null)
                            .ranking(null)
                            .createNew();
            return new MatchContent(match, List.of(lineup), List.of(), List.of(), List.of());
        }
    }
}
