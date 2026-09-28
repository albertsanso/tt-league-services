package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportRunStatusPolicyTest {

    @Test
    void processorFailuresAlwaysFailTheRun() {
        assertEquals(ImportProcessStatus.FAILURE,
                ImportRunStatusPolicy.statusOf(1, false, 5, ImportLifecycleCounters.ZERO));
    }

    @Test
    void issuesAlwaysFailTheRun() {
        assertEquals(ImportProcessStatus.FAILURE,
                ImportRunStatusPolicy.statusOf(0, true, 5, ImportLifecycleCounters.ZERO));
    }

    @Test
    void nothingDispatchedAndNoUnresolvedFixtureIsEmptyResult() {
        assertEquals(ImportProcessStatus.EMPTY_RESULT,
                ImportRunStatusPolicy.statusOf(0, false, 0, ImportLifecycleCounters.ZERO));
    }

    @Test
    void unparsableRunsStayEmptyEvenWithCountersZeroButUnresolvedZero() {
        assertEquals(ImportProcessStatus.EMPTY_RESULT, ImportRunStatusPolicy.statusOf(0, false, 0,
                new ImportLifecycleCounters(0, 0, 0, 1, 1, 0)));
    }

    @Test
    void recognisedUnresolvedPendingFixturesEndSuccessNotEmpty() {
        assertEquals(ImportProcessStatus.SUCCESS, ImportRunStatusPolicy.statusOf(0, false, 0,
                new ImportLifecycleCounters(0, 0, 0, 0, 0, 1)));
    }

    @Test
    void dispatchedRunsEndSuccessEvenWithoutLifecycleActivity() {
        assertEquals(ImportProcessStatus.SUCCESS,
                ImportRunStatusPolicy.statusOf(0, false, 3, ImportLifecycleCounters.ZERO));
        assertEquals(ImportProcessStatus.SUCCESS, ImportRunStatusPolicy.statusOf(0, false, 3,
                new ImportLifecycleCounters(0, 0, 0, 2, 1, 0)));
    }

    @Test
    void aNullLifecycleIsTreatedAsZero() {
        assertEquals(ImportProcessStatus.EMPTY_RESULT,
                ImportRunStatusPolicy.statusOf(0, false, 0, null));
        assertEquals(ImportProcessStatus.SUCCESS,
                ImportRunStatusPolicy.statusOf(0, false, 1, null));
    }
}
