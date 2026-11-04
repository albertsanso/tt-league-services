package org.cttelsamicsterrassa.data.pipeline.runtime.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RunLogContextTest {

    @AfterEach
    void clean() {
        MDC.clear();
    }

    @Test
    void bindsTheRunIdAndRemovesItOnClose() {
        UUID id = UUID.randomUUID();

        try (RunLogContext.Scope scope = RunLogContext.bind(id)) {
            assertThat(MDC.get(RunLogContext.RUN_ID)).isEqualTo(id.toString());
        }

        assertThat(MDC.get(RunLogContext.RUN_ID)).isNull();
    }

    @Test
    void nestedBindsRestoreTheOuterRunId() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();

        try (RunLogContext.Scope a = RunLogContext.bind(outer)) {
            try (RunLogContext.Scope b = RunLogContext.bind(inner)) {
                assertThat(MDC.get(RunLogContext.RUN_ID)).isEqualTo(inner.toString());
            }
            assertThat(MDC.get(RunLogContext.RUN_ID)).isEqualTo(outer.toString());
        }

        assertThat(MDC.get(RunLogContext.RUN_ID)).isNull();
    }

    @Test
    void rejectsANullRunId() {
        assertThatThrownBy(() -> RunLogContext.bind(null)).isInstanceOf(NullPointerException.class);
    }
}
