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

    public void uploadAndTriggerAsyncLoad(String filename, byte[] content, boolean allowPublishedShrink) {
        ImportManifest importManifest = validateUpload(filename, content, allowPublishedShrink);
        triggerAsyncLoad(importManifest);
    }

    /**
     * Validates an upload without storing it: the file, the ZIP, the manifest (including a declared
     * {@code contentSha256}) and the published-acta shrink check.
     *
     * @return the manifest, whose extraction folder still holds the extracted content
     * @throws IllegalArgumentException when the file, ZIP or manifest is invalid
     * @throws SnapshotShrinkException when the upload shrinks published actas and no override is given
     */
    public ImportManifest validateUpload(String filename, byte[] content, boolean allowPublishedShrink) {
        ImportManifest importManifest = readManifest(filename, content);
        resourceRepositoryLoaderService.verifyPublishedActasNotShrinking(importManifest, allowPublishedShrink);
        return importManifest;
    }

    /**
     * Validates the file and the ZIP and returns its manifest, without the published-acta shrink check.
     *
     * @return the manifest, whose extraction folder holds the extracted content
     * @throws IllegalArgumentException when the file, ZIP or manifest is invalid
     */
    public ImportManifest readManifest(String filename, byte[] content) {
        ResourceZipService.validateFile(filename, content);
        return resourceZipService.extractZipAndGetManifest(content);
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
