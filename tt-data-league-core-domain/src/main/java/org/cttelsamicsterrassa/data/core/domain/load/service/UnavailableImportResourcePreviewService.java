package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

import javax.inject.Named;
import java.util.List;

@Named
public class UnavailableImportResourcePreviewService implements ImportResourcePreviewService {

    @Override
    public ImportPreviewResult preview(ImportResource importResource) {
        return ImportPreviewResult.failure(
                List.of(),
                List.of(new ImportPreviewProcessingError(
                        "Import preview is not available in this runtime.",
                        importResource.getResource().getPhysicalPath().toString())),
                0,
                0,
                0,
                0);
    }
}
