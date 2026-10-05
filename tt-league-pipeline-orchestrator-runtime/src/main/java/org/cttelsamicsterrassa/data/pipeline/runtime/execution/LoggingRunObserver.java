package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.runtime.logging.RunLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One INFO line per run or step change; carries ids, statuses and error codes, never keys. The run id is bound in the
 * MDC and the other values are key-value pairs, so JSON logs get them as fields.
 */
public final class LoggingRunObserver implements RunObserver {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingRunObserver.class);

    @Override
    public void runChanged(PipelineRun run) {
        String error = run.error() == null ? "-" : run.error().code();
        try (RunLogContext.Scope scope = RunLogContext.bind(run.id())) {
            LOG.atInfo()
                    .addKeyValue("source", run.source())
                    .addKeyValue("status", run.status())
                    .addKeyValue("error", error)
                    .log("run {} source={} season={} status={} error={}", run.id(), run.source(), run.season(),
                            run.status(), error);
        }
    }

    @Override
    public void stepChanged(PipelineStep step) {
        String outcome = step.outcome() == null ? "-" : step.outcome();
        String error = step.error() == null ? "-" : step.error().code();
        try (RunLogContext.Scope scope = RunLogContext.bind(step.runId())) {
            LOG.atInfo()
                    .addKeyValue("step", step.kind())
                    .addKeyValue("attempt", step.attempt())
                    .addKeyValue("status", step.status())
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("error", error)
                    .log("run {} step={}/{} status={} outcome={} error={}", step.runId(), step.kind(),
                            step.attempt(), step.status(), outcome, error);
        }
    }
}
