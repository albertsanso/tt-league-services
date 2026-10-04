package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One INFO line per run or step change; carries ids, statuses and error codes, never keys. */
public final class LoggingRunObserver implements RunObserver {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingRunObserver.class);

    @Override
    public void runChanged(PipelineRun run) {
        LOG.info("run {} source={} season={} status={} error={}", run.id(), run.source(), run.season(),
                run.status(), run.error() == null ? "-" : run.error().code());
    }

    @Override
    public void stepChanged(PipelineStep step) {
        LOG.info("run {} step={}/{} status={} outcome={} error={}", step.runId(), step.kind(), step.attempt(),
                step.status(), step.outcome() == null ? "-" : step.outcome(),
                step.error() == null ? "-" : step.error().code());
    }
}
