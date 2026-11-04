package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UnitExecutionRulesTest {

    /** The codes that say a service is unavailable or the executor broke: they would repeat for every unit. */
    private static final Set<FailureCode> ABORTING = EnumSet.of(
            FailureCode.INGEST_UNAVAILABLE, FailureCode.INGEST_BUSY, FailureCode.PLATFORM_UNAVAILABLE,
            FailureCode.ARTIFACT_STORE_FAILED, FailureCode.INTERNAL_ERROR);

    @ParameterizedTest
    @EnumSource(FailureCode.class)
    void exactlyTheDocumentedCodesAbortTheRemainingUnits(FailureCode code) {
        assertThat(UnitExecutionRules.abortsRemaining(code.name())).isEqualTo(ABORTING.contains(code));
    }

    @Test
    void anUnknownCodeDoesNotAbort() {
        assertThat(UnitExecutionRules.abortsRemaining("SOMETHING_ELSE")).isFalse();
    }

    @Test
    void theSkipErrorNamesTheAbortingCodeAndUnit() {
        ScopeFilter filter = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
        RunUnit failed = RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 0, UnitKey.of(filter), "SENIOR G1 · J3",
                new RunScope(List.of(filter)))
                .fail(new RunError("INGEST_BUSY", "busy"), Instant.parse("2026-10-04T10:00:00Z"));

        RunError error = UnitExecutionRules.skipError(failed);

        assertThat(error.code()).isEqualTo("UNIT_SKIPPED");
        assertThat(error.message()).isEqualTo("skipped after INGEST_BUSY on unit SENIOR G1 · J3");
    }
}
