package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompleteness;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.AmendedActaMode;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleAction;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlanner;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision-table tests for the pure {@link MatchLifecyclePlanner} (FEAT-00088): every cell of
 * none/SCHEDULED/PLAYED x PLAYED/PENDING/PARTIAL/INVALID, the reschedule merge rule,
 * {@code planCreation}, and that the source is only invoked for the reschedule rule.
 */
class MatchLifecyclePlannerTest {

    private static final Season SEASON = Season.of(2026);
    private static final ZonedDateTime DATE_A = LocalDate.of(2026, 9, 27)
            .atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);
    private static final ZonedDateTime DATE_B = LocalDate.of(2026, 10, 4)
            .atTime(LocalTime.NOON).atZone(Match.COMPETITION_ZONE);

    private final MatchLifecyclePlanner planner = new MatchLifecyclePlanner();
    private Team homeTeam;
    private Team awayTeam;

    @BeforeEach
    void setUp() {
        homeTeam = Team.createNew(ImportSource.RFETM, "HOME", SEASON, null);
        awayTeam = Team.createNew(ImportSource.RFETM, "AWAY", SEASON, null);
    }

    // --- existing = none --------------------------------------------------------------------

    @Test
    void playedWithoutExistingPlansCreatePlayed() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PLAYED),
                Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.PLAYED_CREATED, plan.outcome());
        assertEquals(MatchLifecycleAction.CREATE_PLAYED, plan.action());
        assertNull(plan.mergedSchedule());
        assertFalse(source.scheduledBuilt, "the scheduled header is only built for the reschedule rule");
        assertFalse(source.playedBuilt, "the planner never builds played content");
    }

    @Test
    void pendingWithoutExistingPlansCreateScheduled() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PENDING),
                Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.SCHEDULED_CREATED, plan.outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED, plan.action());
        assertFalse(source.scheduledBuilt);
    }

    @Test
    void partialWithoutExistingPlansCreateScheduledAndReports() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PARTIAL),
                Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.PARTIAL_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED, plan.action());
        assertTrue(plan.outcome().isReportable());
    }

    @Test
    void invalidWithoutExistingPlansCreateScheduledAndReports() {
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.INVALID),
                Optional.empty(), source);

        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED, plan.action());
    }

    // --- existing = SCHEDULED ----------------------------------------------------------------

    @Test
    void playedWithScheduledExistingPlansUpgrade() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "other", "other venue", "other referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PLAYED),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.UPGRADED_TO_PLAYED, plan.outcome());
        assertEquals(MatchLifecycleAction.UPGRADE_TO_PLAYED, plan.action());
        assertFalse(source.scheduledBuilt);
    }

    @Test
    void pendingWithEqualSchedulePlansUnchangedNone() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PENDING),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.UNCHANGED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
        assertTrue(source.scheduledBuilt, "the merge rule needs the incoming schedule");
    }

    @Test
    void pendingWithNullIncomingComponentsKeepsStoredSchedule() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(null, null, null, null);

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PENDING),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.UNCHANGED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
    }

    @Test
    void pendingWithDifferentSchedulePlansUpdateSchedule() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "new city", "new venue", "new referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PENDING),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.RESCHEDULED, plan.outcome());
        assertEquals(MatchLifecycleAction.UPDATE_SCHEDULE, plan.action());
        assertEquals(new MatchSchedule(DATE_B, "new city", "new venue", "new referee", null),
                plan.mergedSchedule());
    }

    @Test
    void partialWithChangedSchedulePlansUpdateScheduleAndReports() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PARTIAL),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.PARTIAL_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.UPDATE_SCHEDULE, plan.action());
        assertEquals(DATE_B, plan.mergedSchedule().dateTime());
        assertEquals("city", plan.mergedSchedule().city());
    }

    @Test
    void invalidWithUnchangedSchedulePlansNoneAndReports() {
        Match stored = scheduled(DATE_A, "city", "venue", "referee");
        FakeSource source = new FakeSource(DATE_A, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.INVALID),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
    }

    // --- existing = PLAYED -------------------------------------------------------------------

    @Test
    void playedWithPlayedExistingPlansKeep() {
        Match stored = played(DATE_A);
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PLAYED),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
        assertFalse(source.scheduledBuilt);
    }

    @Test
    void pendingWithPlayedExistingPlansRegression() {
        Match stored = played(DATE_A);
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PENDING),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.REGRESSION_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
        assertFalse(source.scheduledBuilt);
    }

    @Test
    void partialWithPlayedExistingPlansRegression() {
        Match stored = played(DATE_A);
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.PARTIAL),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.REGRESSION_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
    }

    @Test
    void invalidWithPlayedExistingPlansInvalidReport() {
        Match stored = played(DATE_A);
        FakeSource source = new FakeSource(DATE_B, "city", "venue", "referee");

        MatchLifecyclePlan plan = planner.plan(classification(ActaCompleteness.INVALID),
                Optional.of(stored), source);

        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED, plan.outcome());
        assertEquals(MatchLifecycleAction.NONE, plan.action());
    }

    // --- planCreation ------------------------------------------------------------------------

    @Test
    void planCreationCoversEveryClassification() {
        assertEquals(MatchLifecycleAction.CREATE_PLAYED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PLAYED)).action());
        assertEquals(MatchLifecycleOutcome.PLAYED_CREATED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PLAYED)).outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PENDING)).action());
        assertEquals(MatchLifecycleOutcome.SCHEDULED_CREATED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PENDING)).outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PARTIAL)).action());
        assertEquals(MatchLifecycleOutcome.PARTIAL_REPORTED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.PARTIAL)).outcome());
        assertEquals(MatchLifecycleAction.CREATE_SCHEDULED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.INVALID)).action());
        assertEquals(MatchLifecycleOutcome.INVALID_REPORTED,
                MatchLifecyclePlanner.planCreation(classification(ActaCompleteness.INVALID)).outcome());
    }

    // --- planAmendment (FEAT-00089) ----------------------------------------------------------

    @Test
    void planAmendmentKeepsWhenTheChecksumsAreEqual() {
        Match stored = played(DATE_A).withSourceChecksum("v1:abc");

        MatchLifecyclePlan write = planner.planAmendment(stored, "v1:abc", AmendedActaMode.WRITE);
        MatchLifecyclePlan report = planner.planAmendment(stored, "v1:abc", AmendedActaMode.REPORT);

        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, write.outcome());
        assertEquals(MatchLifecycleAction.NONE, write.action());
        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, report.outcome());
        assertEquals(MatchLifecycleAction.NONE, report.action());
    }

    @Test
    void planAmendmentAdoptsTheBaselineWhenTheStoredChecksumIsNull() {
        Match stored = played(DATE_A);

        MatchLifecyclePlan write = planner.planAmendment(stored, "v1:incoming", AmendedActaMode.WRITE);
        MatchLifecyclePlan report = planner.planAmendment(stored, "v1:incoming", AmendedActaMode.REPORT);

        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, write.outcome());
        assertEquals(MatchLifecycleAction.RECORD_SOURCE_CHECKSUM, write.action());
        assertEquals(MatchLifecycleOutcome.PLAYED_KEPT, report.outcome());
        assertEquals(MatchLifecycleAction.NONE, report.action());
        assertFalse(write.outcome().isReportable());
    }

    @Test
    void planAmendmentAdoptsTheBaselineWhenTheStoredChecksumHasAForeignPrefix() {
        Match stored = played(DATE_A).withSourceChecksum("sha256:old");

        assertEquals(MatchLifecycleAction.RECORD_SOURCE_CHECKSUM,
                planner.planAmendment(stored, "v1:incoming", AmendedActaMode.WRITE).action());
        assertEquals(MatchLifecycleAction.NONE,
                planner.planAmendment(stored, "v1:incoming", AmendedActaMode.REPORT).action());
    }

    @Test
    void planAmendmentReappliesWhenTheChecksumsDiffer() {
        Match stored = played(DATE_A).withSourceChecksum("v1:old");

        MatchLifecyclePlan write = planner.planAmendment(stored, "v1:new", AmendedActaMode.WRITE);
        MatchLifecyclePlan report = planner.planAmendment(stored, "v1:new", AmendedActaMode.REPORT);

        assertEquals(MatchLifecycleOutcome.PLAYED_AMENDED, write.outcome());
        assertEquals(MatchLifecycleAction.REAPPLY_PLAYED, write.action());
        assertTrue(write.outcome().isReportable());
        assertEquals(MatchLifecycleOutcome.PLAYED_AMENDMENT_REPORTED, report.outcome());
        assertEquals(MatchLifecycleAction.NONE, report.action());
        assertTrue(report.outcome().isReportable());
    }

    @Test
    void planAmendmentRequiresAPlayedMatchAndNonNullArguments() {
        Match scheduled = scheduled(DATE_A, "city", "venue", "referee");
        Match played = played(DATE_A);

        assertThrows(IllegalArgumentException.class,
                () -> planner.planAmendment(scheduled, "v1:a", AmendedActaMode.WRITE));
        assertThrows(NullPointerException.class,
                () -> planner.planAmendment(null, "v1:a", AmendedActaMode.WRITE));
        assertThrows(NullPointerException.class,
                () -> planner.planAmendment(played, null, AmendedActaMode.WRITE));
        assertThrows(NullPointerException.class,
                () -> planner.planAmendment(played, "v1:a", null));
    }

    // --- plan record -------------------------------------------------------------------------

    @Test
    void planRejectsMergedScheduleOutsideUpdateSchedule() {
        MatchSchedule schedule = new MatchSchedule(DATE_A, "city", "venue", "referee", null);
        assertThrows(IllegalArgumentException.class,
                () -> new MatchLifecyclePlan(MatchLifecycleOutcome.RESCHEDULED,
                        MatchLifecycleAction.NONE, schedule));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchLifecyclePlan(MatchLifecycleOutcome.RESCHEDULED,
                        MatchLifecycleAction.UPDATE_SCHEDULE, null));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static ActaClassification classification(ActaCompleteness completeness) {
        return new ActaClassification(completeness, "test " + completeness, false);
    }

    private Match scheduled(ZonedDateTime date, String city, String venue, String referee) {
        return Match.builder()
                .id(UUID.randomUUID())
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
                .status(MatchStatus.SCHEDULED)
                .createNew();
    }

    private Match played(ZonedDateTime date) {
        return Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition("divisio-honor-masculino")
                .season(SEASON)
                .groupNumber(0)
                .round(1)
                .dateTime(date)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeGamesWon(3)
                .awayGamesWon(0)
                .winnerTeam(homeTeam)
                .status(MatchStatus.PLAYED)
                .createNew();
    }

    private final class FakeSource implements MatchLifecycleSource {

        private final ZonedDateTime date;
        private final String city;
        private final String venue;
        private final String referee;
        private boolean scheduledBuilt;
        private boolean playedBuilt;

        private FakeSource(ZonedDateTime date, String city, String venue, String referee) {
            this.date = date;
            this.city = city;
            this.venue = venue;
            this.referee = referee;
        }

        @Override
        public Match buildScheduledMatch(UUID id) {
            scheduledBuilt = true;
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
                    .status(MatchStatus.SCHEDULED)
                    .createNew();
        }

        @Override
        public MatchContent buildPlayedContent(UUID id, boolean existing) {
            playedBuilt = true;
            throw new AssertionError("the planner must never build played content");
        }
    }

}
