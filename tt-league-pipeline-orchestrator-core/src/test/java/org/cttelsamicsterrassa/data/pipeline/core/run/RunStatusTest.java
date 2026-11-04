package org.cttelsamicsterrassa.data.pipeline.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.FAILED;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.NO_CHANGES;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.PARTIAL;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.QUEUED;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.RUNNING;
import static org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus.SUCCEEDED;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RunStatusTest {

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED = Map.of(
            QUEUED, Set.of(RUNNING, FAILED),
            RUNNING, Set.of(NO_CHANGES, SUCCEEDED, PARTIAL, FAILED),
            NO_CHANGES, Set.of(),
            SUCCEEDED, Set.of(),
            PARTIAL, Set.of(),
            FAILED, Set.of());

    static Stream<Arguments> allPairs() {
        return Stream.of(RunStatus.values())
                .flatMap(from -> Stream.of(RunStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void allowsExactlyTheDocumentedTransitions(RunStatus from, RunStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.get(from).contains(to));
    }

    @Test
    void activeStatusesAreTheNonTerminalOnes() {
        assertThat(RunStatus.active()).containsExactlyInAnyOrder(QUEUED, RUNNING);
        for (RunStatus status : RunStatus.values()) {
            assertThat(status.isActive()).isEqualTo(RunStatus.active().contains(status));
            assertThat(status.isTerminal()).isEqualTo(!status.isActive());
        }
    }
}
