package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunRecovery;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.artifact.FileSystemArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpClientsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpImportGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.IngestServiceJobRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
    RunObserver runObserver() {
        return new LoggingRunObserver();
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
    RunLauncher runLauncher(PipelineRunRepository runs, RunDispatcher dispatcher, RunClock clock) {
        return new RunLauncher(runs, dispatcher, clock);
    }

    @Bean
    RunRecovery runRecovery(PipelineRunRepository runs, RunDispatcher dispatcher) {
        return new RunRecovery(runs, dispatcher);
    }

    /** Resumes runs left active by a restart. A run created meanwhile is protected by the in-flight set. */
    @EventListener(ApplicationReadyEvent.class)
    void recoverRuns(ApplicationReadyEvent event) {
        PipelineOrchestratorProperties properties = event.getApplicationContext()
                .getBean(PipelineOrchestratorProperties.class);
        if (!properties.execution().recoverOnStartup()) {
            LOG.info("run recovery on startup is disabled");
            return;
        }
        int count = event.getApplicationContext().getBean(RunRecovery.class).recover();
        LOG.info("recovered {} active run(s)", count);
    }
}
