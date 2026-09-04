package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourcePreviewService;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
@Primary
public class NavigatorBackedImportResourcePreviewService implements ImportResourcePreviewService {

    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;
    private final FcttActasDirectoryNavigator fcttNavigator;

    public NavigatorBackedImportResourcePreviewService(RfetmActasDirectoryNavigator rfetmNavigator,
                                                       BcnesaActasDirectoryNavigator bcnesaNavigator,
                                                       FcttActasDirectoryNavigator fcttNavigator) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
        this.fcttNavigator = fcttNavigator;
    }

    @Override
    public ImportPreviewResult preview(ImportResource importResource) {
        if (importResource.getType() != ResourceType.ACTAS) {
            return unsupportedType(importResource);
        }

        ImportPreviewCollector collector = new ImportPreviewCollector();
        Path baseFolder = importResource.getResource().getPhysicalPath();
        String season = importResource.getSeason().toString();
        try {
            return switch (importResource.getSource()) {
                case RFETM -> rfetmResult(baseFolder, season, collector);
                case BCNESA -> bcnesaResult(baseFolder, season, collector);
                case FCTT -> fcttResult(baseFolder, season, collector);
            };
        } catch (IOException exception) {
            return ImportPreviewResult.failure(
                    List.of(),
                    List.of(new ImportPreviewProcessingError(exception.getMessage(), baseFolder.toString())),
                    0,
                    0,
                    0,
                    0);
        }
    }

    private ImportPreviewResult rfetmResult(Path baseFolder, String season, ImportPreviewCollector collector)
            throws IOException {
        TraversalSummary summary = rfetmNavigator.traverseSeason(
                baseFolder, season, List.of(new RfetmPreviewValidationProcessor(collector)));
        return collector.toResult(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                summary.processorFailures());
    }

    private ImportPreviewResult bcnesaResult(Path baseFolder, String season, ImportPreviewCollector collector)
            throws IOException {
        BcnesaTraversalSummary summary = bcnesaNavigator.traverseSeason(
                baseFolder, season, List.of(new BcnesaPreviewValidationProcessor(collector)));
        long skipped = summary.filesSkipped() + summary.fixturesUnresolved();
        return collector.toResult(summary.filesSeen(), summary.fixturesDispatched(), skipped,
                summary.processorFailures());
    }

    private ImportPreviewResult fcttResult(Path baseFolder, String season, ImportPreviewCollector collector)
            throws IOException {
        TraversalSummary summary = fcttNavigator.traverseSeason(
                baseFolder, season, List.of(new FcttPreviewValidationProcessor(collector)));
        return collector.toResult(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                summary.processorFailures());
    }

    private ImportPreviewResult unsupportedType(ImportResource importResource) {
        String message = "%s preview supports ACTAS resources only; %s resources have no match-report navigator."
                .formatted(importResource.getSource() == null ? ImportSource.RFETM : importResource.getSource(),
                        importResource.getType());
        return ImportPreviewResult.failure(
                List.of(),
                List.of(new ImportPreviewProcessingError(
                        message,
                        importResource.getResource().getPhysicalPath().toString())),
                0,
                0,
                0,
                0);
    }
}
