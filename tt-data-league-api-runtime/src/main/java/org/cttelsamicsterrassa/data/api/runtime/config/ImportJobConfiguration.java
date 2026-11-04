package org.cttelsamicsterrassa.data.api.runtime.config;

import org.cttelsamicsterrassa.data.api.runtime.importjob.ExecutorImportJobDispatcher;
import org.cttelsamicsterrassa.data.core.application.importjob.SubmitImportJobCommandHandler;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobRepository;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobService;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobSettings;
import org.cttelsamicsterrassa.data.core.domain.load.repository.ImportResourceRepository;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceRunService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceRepositoryLoaderService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceUploadService;
import org.cttelsamicsterrassa.data.core.domain.load.service.ResourceZipService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the import jobs (FEAT-00100). The job service and its submit handler are declared here rather than
 * component-scanned, so the import runtime, which scans every package but runs no jobs, never needs the
 * dispatcher or the job settings.
 */
@Configuration
@EnableConfigurationProperties(ImportJobProperties.class)
public class ImportJobConfiguration {

    @Bean
    ImportJobSettings importJobSettings(ImportJobProperties properties) {
        return properties.toSettings();
    }

    /**
     * The dispatcher calls back into the job service, which needs the dispatcher; the provider resolves the
     * service lazily, when the first job runs.
     */
    @Bean
    ExecutorImportJobDispatcher importJobDispatcher(ObjectProvider<ImportJobService> importJobService) {
        return new ExecutorImportJobDispatcher(jobId -> importJobService.getObject().execute(jobId));
    }

    @Bean
    ImportJobService importJobService(ResourceUploadService uploadService,
                                      ResourceZipService zipService,
                                      ResourceRepositoryLoaderService loaderService,
                                      ImportRunRegistry runRegistry,
                                      ImportResourceRunService runService,
                                      ImportResourceRepository importResourceRepository,
                                      ImportJobRepository jobRepository,
                                      ExecutorImportJobDispatcher dispatcher,
                                      ImportJobSettings settings) {
        return new ImportJobService(uploadService, zipService, loaderService, runRegistry, runService,
                importResourceRepository, jobRepository, dispatcher, settings);
    }

    @Bean
    SubmitImportJobCommandHandler submitImportJobCommandHandler(ImportJobService importJobService) {
        return new SubmitImportJobCommandHandler(importJobService);
    }

    /**
     * Fails the jobs a restart interrupted and resumes the queued ones once every singleton exists, which is
     * before the web server accepts requests, so a job submitted right after startup is never mistaken for an
     * interrupted one. A failure propagates, so the application does not start with unrecovered jobs.
     */
    @Bean
    SmartInitializingSingleton importJobRecovery(ImportJobService importJobService) {
        return importJobService::recoverAfterRestart;
    }
}
