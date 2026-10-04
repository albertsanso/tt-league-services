package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunRecovery;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.CompositeRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTriggerDrainer;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.runtime.artifact.FileSystemArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.RunEventBroadcaster;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpClientsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpImportGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.IngestServiceJobRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;

/** Wires the run executor from the public core API, the JPA repositories and the HTTP gateways. */
@Configuration(proxyBeanMethods = false)
@Import(HttpClientsConfiguration.class)
public class RunExecutionConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RunExecutionConfiguration.class);

    @Bean
    IngestGateway ingestGateway(@Qualifier("ingestRestClient") RestClient client) {
        return new IngestServiceJobRunner(client);
    }

    @Bean
    ImportGateway importGateway(@Qualifier("platformRestClient") RestClient client, ObjectMapper json) {
        return new HttpImportGateway(client, json);
    }

    @Bean
    ArtifactStore artifactStore(PipelineOrchestratorProperties properties) {
        return new FileSystemArtifactStore(properties.artifacts().dir());
    }

    @Bean
    RunClock runClock() {
        return new SystemRunClock();
    }

    @Bean
    LoggingRunObserver loggingRunObserver() {
        return new LoggingRunObserver();
    }

    /**
     * Logging, the event stream, the pending-trigger drainer and the tracker recompute request. The parts are injected by concrete type, so this
     * primary bean is the only {@link RunObserver} the executor and the launcher see. The drainer gets the trigger
     * use case lazily: {@code TriggerRun -> RunLauncher -> RunObserver -> drainer -> TriggerRun} would be a cycle.
     */
    @Bean
    @Primary
    RunObserver runObserver(
            LoggingRunObserver logging,
            RunEventBroadcaster broadcaster,
            ObjectProvider<TriggerRun> triggerRun,
            TrackerRunObserver tracker) {
        return CompositeRunObserver.of(
                List.of(logging, broadcaster, new PendingTriggerDrainer(triggerRun::getObject), tracker));
    }

    @Bean
    ExecutionSettings executionSettings(PipelineOrchestratorProperties properties) {
        return properties.executionSettings();
    }

    @Bean
    RunExecutor runExecutor(
            PipelineRunRepository runs,
            PipelineStepRepository steps,
            RunArtifactRepository artifactRows,
            ImportReportRepository reports,
            IngestGateway ingest,
            ImportGateway importGateway,
            ArtifactStore artifacts,
            RunClock clock,
            RunObserver observer,
            ExecutionSettings settings) {
        return new RunExecutor(runs, steps, artifactRows, reports, ingest, importGateway, artifacts, clock,
                observer, settings);
    }

    @Bean
    RunDispatcher runDispatcher(RunExecutor executor, PipelineOrchestratorProperties properties) {
        return new ExecutorRunDispatcher(executor, properties.execution().maxConcurrentRuns());
    }

    @Bean
    RunLauncher runLauncher(
            PipelineRunRepository runs, RunDispatcher dispatcher, RunClock clock, RunObserver observer) {
        return new RunLauncher(runs, dispatcher, clock, observer);
    }

    @Bean
    RunRecovery runRecovery(PipelineRunRepository runs, RunDispatcher dispatcher) {
        return new RunRecovery(runs, dispatcher);
    }

    /**
     * Resumes runs left active by a restart (a run created meanwhile is protected by the in-flight set), then
     * launches pending triggers whose source has no active run.
     */
    @EventListener(ApplicationReadyEvent.class)
    void recoverRuns(ApplicationReadyEvent event) {
        PipelineOrchestratorProperties properties = event.getApplicationContext()
                .getBean(PipelineOrchestratorProperties.class);
        if (properties.execution().recoverOnStartup()) {
            int count = event.getApplicationContext().getBean(RunRecovery.class).recover();
            LOG.info("recovered {} active run(s)", count);
        } else {
            LOG.info("run recovery on startup is disabled");
        }
        int launched = event.getApplicationContext().getBean(TriggerRun.class).drainIdle();
        LOG.info("launched {} pending trigger(s) of idle sources", launched);
    }
}
