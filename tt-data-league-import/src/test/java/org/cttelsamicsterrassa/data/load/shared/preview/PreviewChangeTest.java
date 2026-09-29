package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleAction;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00088: the plan-to-change mapping the preview reuses, so a projected change can never drift
 * from the lifecycle decision it is derived from.
 */
class PreviewChangeTest {

    @Test
    void createActionsMapToNewChanges() {
        assertEquals(PreviewChange.NEW_SCHEDULED, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.SCHEDULED_CREATED,
                        MatchLifecycleAction.CREATE_SCHEDULED)));
        assertEquals(PreviewChange.NEW_PLAYED, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.PLAYED_CREATED,
                        MatchLifecycleAction.CREATE_PLAYED)));
    }

    @Test
    void upgradeAndRescheduleMapByAction() {
        assertEquals(PreviewChange.UPGRADE, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.UPGRADED_TO_PLAYED,
                        MatchLifecycleAction.UPGRADE_TO_PLAYED)));
        // A reschedule is keyed on the action, whatever outcome is reported alongside it.
        assertEquals(PreviewChange.RESCHEDULE, PreviewChange.of(
                MatchLifecyclePlan.reschedule(MatchLifecycleOutcome.RESCHEDULED,
                        new MatchSchedule(null, null, null, null, null))));
        assertEquals(PreviewChange.RESCHEDULE, PreviewChange.of(
                MatchLifecyclePlan.reschedule(MatchLifecycleOutcome.PARTIAL_REPORTED,
                        new MatchSchedule(null, null, null, null, null))));
    }

    @Test
    void noneActionIsClassifiedByOutcome() {
        assertEquals(PreviewChange.REGRESSION, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.REGRESSION_REPORTED, MatchLifecycleAction.NONE)));
        assertEquals(PreviewChange.INVALID_ON_PLAYED, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.INVALID_REPORTED, MatchLifecycleAction.NONE)));
        assertEquals(PreviewChange.PLAYED_KEPT, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.PLAYED_KEPT, MatchLifecycleAction.NONE)));
        assertEquals(PreviewChange.UNCHANGED, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.UNCHANGED, MatchLifecycleAction.NONE)));
        assertEquals(PreviewChange.UNCHANGED, PreviewChange.of(
                MatchLifecyclePlan.of(MatchLifecycleOutcome.PARTIAL_REPORTED, MatchLifecycleAction.NONE)));
    }

    @Test
    void storedChangesAndReportableChangesAreFlagged() {
        assertTrue(PreviewChange.NEW_SCHEDULED.isStoredChange());
        assertTrue(PreviewChange.NEW_PLAYED.isStoredChange());
        assertTrue(PreviewChange.UPGRADE.isStoredChange());
        assertTrue(PreviewChange.RESCHEDULE.isStoredChange());
        assertFalse(PreviewChange.UNCHANGED.isStoredChange());
        assertFalse(PreviewChange.PLAYED_KEPT.isStoredChange());
        assertFalse(PreviewChange.NOT_STORED.isStoredChange());

        assertTrue(PreviewChange.REGRESSION.isReportable());
        assertTrue(PreviewChange.INVALID_ON_PLAYED.isReportable());
        assertTrue(PreviewChange.IDENTITY_CONFLICT.isReportable());
        assertFalse(PreviewChange.NEW_PLAYED.isReportable());
    }
}
