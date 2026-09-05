package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;

import javax.inject.Named;
import java.util.List;

@Named
public class UnavailableImportResourceProcessService implements ImportResourceProcessService {

    @Override
    public ImportProcessResult process(ImportResource importResource) {
        return ImportProcessResult.failure(List.of(), List.of(new ImportPreviewProcessingError(
                "Import processing is not available in this runtime.",
                importResource.getResource().getPhysicalPath().toString())), 0, 0, 0, 0);
    }
}
