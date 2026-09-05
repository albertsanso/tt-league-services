package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourceProcessService;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
@Primary
public class NavigatorBackedImportResourceProcessService implements ImportResourceProcessService {
    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;
    private final FcttActasDirectoryNavigator fcttNavigator;
    private final List<MatchContextProcessor> rfetmProcessors;
    private final List<BcnesaMatchReportProcessor> bcnesaProcessors;
    private final List<FcttMatchReportProcessor> fcttProcessors;

    public NavigatorBackedImportResourceProcessService(RfetmActasDirectoryNavigator rfetmNavigator,
                                                       BcnesaActasDirectoryNavigator bcnesaNavigator,
                                                       FcttActasDirectoryNavigator fcttNavigator,
                                                       List<MatchContextProcessor> rfetmProcessors,
                                                       List<BcnesaMatchReportProcessor> bcnesaProcessors,
                                                       List<FcttMatchReportProcessor> fcttProcessors) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
        this.fcttNavigator = fcttNavigator;
        this.rfetmProcessors = rfetmProcessors;
        this.bcnesaProcessors = bcnesaProcessors;
        this.fcttProcessors = fcttProcessors;
    }

    @Override
    public ImportProcessResult process(ImportResource resource) {
        if (resource.getType() != ResourceType.ACTAS) {
            return ImportProcessResult.failure(List.of(), List.of(new ImportPreviewProcessingError(
                    "%s import supports ACTAS resources only; %s resources have no match-report navigator."
                            .formatted(resource.getSource() == null ? ImportSource.RFETM : resource.getSource(),
                                    resource.getType()),
                    resource.getResource().getPhysicalPath().toString())), 0, 0, 0, 0);
        }
        Path baseFolder = resource.getResource().getPhysicalPath();
        ImportProcessCollector collector = new ImportProcessCollector();
        try {
            return switch (resource.getSource()) {
                case RFETM -> rfetmResult(baseFolder, resource.getSeason().toString(), collector);
                case BCNESA -> bcnesaResult(baseFolder, resource.getSeason().toString(), collector);
                case FCTT -> fcttResult(baseFolder, resource.getSeason().toString(), collector);
            };
        } catch (IOException exception) {
            return ImportProcessResult.failure(List.of(),
                    List.of(new ImportPreviewProcessingError(exception.getMessage(), baseFolder.toString())),
                    0, 0, 0, 0);
        }
    }

    private ImportProcessResult rfetmResult(Path folder, String season, ImportProcessCollector collector)
            throws IOException {
        List<MatchContextProcessor> processors = rfetmProcessors.stream()
                .<MatchContextProcessor>map(processor -> new RfetmProcessRecordingProcessor(processor, collector)).toList();
        TraversalSummary summary = rfetmNavigator.traverseSeason(folder, season, processors);
        return collector.toResult(summary.filesSeen(), summary.dispatched(), summary.skipped(), summary.processorFailures());
    }

    private ImportProcessResult bcnesaResult(Path folder, String season, ImportProcessCollector collector)
            throws IOException {
        List<BcnesaMatchReportProcessor> processors = bcnesaProcessors.stream()
                .<BcnesaMatchReportProcessor>map(processor -> new BcnesaProcessRecordingProcessor(processor, collector)).toList();
        BcnesaTraversalSummary summary = bcnesaNavigator.traverseSeason(folder, season, processors);
        return collector.toResult(summary.filesSeen(), summary.fixturesDispatched(),
                summary.filesSkipped() + summary.fixturesUnresolved(), summary.processorFailures());
    }

    private ImportProcessResult fcttResult(Path folder, String season, ImportProcessCollector collector)
            throws IOException {
        List<FcttMatchReportProcessor> processors = fcttProcessors.stream()
                .<FcttMatchReportProcessor>map(processor -> new FcttProcessRecordingProcessor(processor, collector)).toList();
        TraversalSummary summary = fcttNavigator.traverseSeason(folder, season, processors);
        return collector.toResult(summary.filesSeen(), summary.dispatched(), summary.skipped(), summary.processorFailures());
    }
}
