package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LoggingRunObserverTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingRunObserver.class);
    private final LoggingRunObserver observer = new LoggingRunObserver();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2025-2026", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "tester", null, NOW);
    }

    private static Map<String, Object> pairs(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }

    @Test
    void aRunLineCarriesTheRunIdInTheMdcAndTheOtherValuesAsKeyValuePairs() {
        PipelineRun run = run();

        observer.runChanged(run);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry("runId", run.id().toString());
        assertThat(pairs(event)).containsEntry("source", PipelineSource.FCTT).containsEntry("error", "-")
                .containsKey("status");
        assertThat(event.getFormattedMessage()).contains("run " + run.id()).contains("source=FCTT");
    }

    @Test
    void aStepLineCarriesTheStepFields() {
        PipelineRun run = run();
        PipelineStep step = PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.INGEST, 2, NOW, null);

        observer.stepChanged(step);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry("runId", run.id().toString());
        assertThat(pairs(event)).containsEntry("step", StepKind.INGEST).containsEntry("attempt", 2)
                .containsEntry("outcome", "-");
    }

    @Test
    void anOuterBindingIsRestored() {
        UUID outer = UUID.randomUUID();
        MDC.put("runId", outer.toString());

        observer.runChanged(run());

        assertThat(MDC.get("runId")).isEqualTo(outer.toString());
    }
}
