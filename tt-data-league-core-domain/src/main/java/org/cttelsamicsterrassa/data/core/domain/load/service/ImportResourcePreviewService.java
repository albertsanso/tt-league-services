package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;

public interface ImportResourcePreviewService {

    ImportPreviewResult preview(ImportResource importResource);
}
