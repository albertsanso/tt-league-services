package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.resource.model.ImportManifest;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
public class ResourceUploadService {

    private static final Logger LOGGER = Logger.getLogger(ResourceUploadService.class.getName());

    private final ResourceRepositoryLoaderService resourceRepositoryLoaderService;
    private final ResourceZipService resourceZipService;
    private final Executor backgroundExecutor;

    @Inject
    public ResourceUploadService(ResourceRepositoryLoaderService resourceRepositoryLoaderService,
                                 ResourceZipService resourceZipService,
                                 Executor backgroundExecutor) {
        this.resourceRepositoryLoaderService = resourceRepositoryLoaderService;
        this.resourceZipService = resourceZipService;
        this.backgroundExecutor = backgroundExecutor;
    }

    public void uploadAndTriggerAsyncLoad(String filename, byte[] content) {
        ResourceZipService.validateFile(filename, content);
        triggerAsyncLoad(resourceZipService.extractZipAndGetManifest(content));
    }

    private void triggerAsyncLoad(ImportManifest importManifest) {
        backgroundExecutor.execute(() -> {
            try {
                resourceRepositoryLoaderService.loadIntoRepository(importManifest);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Unable to load uploaded import resource", exception);
            }
        });
    }
}
