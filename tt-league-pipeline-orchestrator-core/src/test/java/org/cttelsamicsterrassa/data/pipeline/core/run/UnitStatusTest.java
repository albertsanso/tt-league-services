package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.FAILED;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.IMPORTING;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.NO_CHANGES;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.PACKED;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.PARTIAL;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.PENDING;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.RUNNING_INGEST;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.SKIPPED;
import static org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus.SUCCEEDED;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnitStatusTest {

    private static final Map<UnitStatus, Set<UnitStatus>> ALLOWED = Map.of(
            PENDING, Set.of(RUNNING_INGEST, PACKED, SKIPPED, FAILED),
            RUNNING_INGEST, Set.of(NO_CHANGES, PACKED, FAILED),
            PACKED, Set.of(IMPORTING, FAILED),
            IMPORTING, Set.of(SUCCEEDED, PARTIAL, FAILED),
            NO_CHANGES, Set.of(),
            SUCCEEDED, Set.of(),
            PARTIAL, Set.of(),
            FAILED, Set.of(),
            SKIPPED, Set.of());

    static Stream<Arguments> allPairs() {
        return Stream.of(UnitStatus.values())
                .flatMap(from -> Stream.of(UnitStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void allowsExactlyTheDocumentedTransitions(UnitStatus from, UnitStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.get(from).contains(to));
    }

    @Test
    void activeStatusesAreTheNonTerminalOnes() {
        for (UnitStatus status : UnitStatus.values()) {
            assertThat(status.isActive()).isEqualTo(!ALLOWED.get(status).isEmpty());
            assertThat(status.isTerminal()).isEqualTo(!status.isActive());
        }
    }

    @Test
    void onlyTheWorkingStatusesCanReportProgress() {
        for (UnitStatus status : UnitStatus.values()) {
            assertThat(status.isRunning()).isEqualTo(Set.of(RUNNING_INGEST, PACKED, IMPORTING).contains(status));
        }
    }
}
