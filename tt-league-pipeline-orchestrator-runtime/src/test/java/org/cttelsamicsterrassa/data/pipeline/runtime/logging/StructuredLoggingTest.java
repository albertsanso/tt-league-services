package org.cttelsamicsterrassa.data.pipeline.runtime.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.runtime.execution.LoggingRunObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** Boot's logstash format puts the MDC run id and the key-value pairs into JSON fields, JUL bridge included. */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {}

    @Test
    void runIdAndKeyValuePairsAreJsonFields(CapturedOutput output) {
        UUID id = UUID.randomUUID();
        SpringApplication application = new SpringApplication(Empty.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(java.util.Map.of(
                "logging.structured.format.console", "logstash",
                "spring.main.banner-mode", "off"));

        try (ConfigurableApplicationContext context = application.run()) {
            try (RunLogContext.Scope scope = RunLogContext.bind(id)) {
                LoggerFactory.getLogger("structured.slf4j").info("from slf4j");
                System.getLogger("structured.jul").log(System.Logger.Level.INFO, "from the core logger");
                new LoggingRunObserver().runChanged(PipelineRun.queue(id, PipelineSource.FCTT, "2025-2026",
                        RunScope.fullSeason(), false, RunTrigger.MANUAL, "tester", null, Instant.now()));
            }
        }

        String runId = "\"runId\":\"" + id + "\"";
        assertThat(lineWith(output, "from slf4j")).startsWith("{").contains(runId);
        assertThat(lineWith(output, "from the core logger")).startsWith("{").contains(runId);
        assertThat(lineWith(output, "source=FCTT")).contains(runId).contains("\"source\":\"FCTT\"")
                .contains("\"status\":\"QUEUED\"");
    }

    private static String lineWith(CapturedOutput output, String text) {
        return output.getOut().lines().filter(line -> line.contains(text)).findFirst()
                .orElseThrow(() -> new AssertionError("no log line with '" + text + "' in:\n" + output.getOut()));
    }

}
