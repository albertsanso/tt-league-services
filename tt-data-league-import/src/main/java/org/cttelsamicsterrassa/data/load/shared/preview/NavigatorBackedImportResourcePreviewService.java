package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewClassification;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportResource;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewActaCounts;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewDuplicateFixtureId;
import org.cttelsamicsterrassa.data.core.domain.load.model.PreviewScopeChanges;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.resource.model.ResourceType;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportResourcePreviewService;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPreviewClassificationProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPreviewClassificationProcessor;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPreviewClassificationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Synchronous, read-only import preview (FEAT-00088). For each source it runs the validation
 * processor and the classification adapter over one traversal, then projects the incremental-upload
 * classification (acta buckets, planned changes, current and projected jornada progress and the
 * duplicated {@code id_partido}s) from the shared {@link IncrementalPreviewCollector} and the stored
 * round-status counts. The classification never changes the preview status, never skips files and
 * never writes.
 */
@Component
@Primary
public class NavigatorBackedImportResourcePreviewService implements ImportResourcePreviewService {

    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;
    private final FcttActasDirectoryNavigator fcttNavigator;
    private final RfetmMatchImportProcessor rfetmMatchProcessor;
    private final BcnesaMatchImportProcessor bcnesaMatchProcessor;
    private final FcttMatchImportProcessor fcttMatchProcessor;
    private final MatchRepository matchRepository;

    public NavigatorBackedImportResourcePreviewService(RfetmActasDirectoryNavigator rfetmNavigator,
                                                       BcnesaActasDirectoryNavigator bcnesaNavigator,
                                                       FcttActasDirectoryNavigator fcttNavigator,
                                                       RfetmMatchImportProcessor rfetmMatchProcessor,
                                                       BcnesaMatchImportProcessor bcnesaMatchProcessor,
                                                       FcttMatchImportProcessor fcttMatchProcessor,
                                                       MatchRepository matchRepository) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
        this.fcttNavigator = fcttNavigator;
        this.rfetmMatchProcessor = rfetmMatchProcessor;
        this.bcnesaMatchProcessor = bcnesaMatchProcessor;
        this.fcttMatchProcessor = fcttMatchProcessor;
        this.matchRepository = matchRepository;
    }

    @Override
    public ImportPreviewResult preview(ImportResource importResource) {
        if (importResource.getType() != ResourceType.ACTAS) {
            return unsupportedType(importResource);
        }

        Path baseFolder = importResource.getResource().getPhysicalPath();
        Season season = importResource.getSeason();
        String seasonFolder = season.toString();
        ImportSource source = importResource.getSource();
        ImportPreviewCollector collector = new ImportPreviewCollector();
        IncrementalPreviewCollector incremental = new IncrementalPreviewCollector();
        ImportRunContext runContext = new ImportRunContext(source, seasonFolder);
        try {
            Traversal traversal = switch (source) {
                case RFETM -> traverseRfetm(baseFolder, seasonFolder, collector, incremental, runContext);
                case BCNESA -> traverseBcnesa(baseFolder, seasonFolder, collector, incremental, runContext);
                case FCTT -> traverseFctt(baseFolder, seasonFolder, collector, incremental, runContext);
            };
            return buildResult(source, season, collector, incremental, runContext, traversal);
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

    private Traversal traverseRfetm(Path baseFolder, String seasonFolder, ImportPreviewCollector collector,
                                    IncrementalPreviewCollector incremental, ImportRunContext runContext)
            throws IOException {
        TraversalSummary summary = rfetmNavigator.traverseSeason(baseFolder, seasonFolder,
                List.of(new RfetmPreviewValidationProcessor(collector),
                        new RfetmPreviewClassificationProcessor(rfetmMatchProcessor, incremental)),
                runContext);
        return new Traversal(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                summary.processorFailures());
    }

    private Traversal traverseBcnesa(Path baseFolder, String seasonFolder, ImportPreviewCollector collector,
                                     IncrementalPreviewCollector incremental, ImportRunContext runContext)
            throws IOException {
        BcnesaTraversalSummary summary = bcnesaNavigator.traverseSeason(baseFolder, seasonFolder,
                List.of(new BcnesaPreviewValidationProcessor(collector),
                        new BcnesaPreviewClassificationProcessor(bcnesaMatchProcessor, incremental)),
                runContext);
        long skipped = summary.filesSkipped() + summary.fixturesUnresolved();
        return new Traversal(summary.filesSeen(), summary.fixturesDispatched(), skipped,
                summary.processorFailures());
    }

    private Traversal traverseFctt(Path baseFolder, String seasonFolder, ImportPreviewCollector collector,
                                   IncrementalPreviewCollector incremental, ImportRunContext runContext)
            throws IOException {
        TraversalSummary summary = fcttNavigator.traverseSeason(baseFolder, seasonFolder,
                List.of(new FcttPreviewValidationProcessor(collector),
                        new FcttPreviewClassificationProcessor(fcttMatchProcessor, incremental)),
                runContext);
        return new Traversal(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                summary.processorFailures());
    }

    private ImportPreviewResult buildResult(ImportSource source, Season season, ImportPreviewCollector collector,
                                            IncrementalPreviewCollector incremental, ImportRunContext runContext,
                                            Traversal traversal) {
        incremental.addNavigatorUnresolved(runContext.lifecycleCounters().unresolvedPendingFixtures());
        List<RoundProgress> current = matchRepository.findRoundProgress(source, season);
        List<RoundProgress> projected = RoundProgressCalculator.compute(source, season,
                incremental.project(matchRepository.findRoundStatusCounts(source, season)));
        ImportPreviewClassification classification = new ImportPreviewClassification(
                incremental.actaCounts(),
                incremental.scopeChanges(),
                incremental.teamsPendingRegistration(),
                current,
                projected,
                incremental.duplicateFixtureIds());
        addClassificationFindings(collector, classification, incremental);
        return collector.toResult(traversal.filesSeen(), traversal.dispatched(), traversal.skipped(),
                traversal.processorFailures(), classification);
    }

    private void addClassificationFindings(ImportPreviewCollector collector,
                                           ImportPreviewClassification classification,
                                           IncrementalPreviewCollector incremental) {
        PreviewActaCounts actas = classification.actas();
        collector.info("Preview classified acta(s): %d published, %d unpublished, %d partial, %d invalid, "
                        + "%d unresolved.".formatted(actas.published(), actas.unpublished(), actas.partial(),
                        actas.invalid(), actas.unresolved()), null);
        if (classification.teamsPendingRegistration() > 0) {
            collector.info("Preview found %d fixture(s) whose teams are not registered yet; the import run "
                    + "registers them before storing the match.".formatted(classification.teamsPendingRegistration()),
                    null);
        }
        for (PreviewScopeChanges change : classification.changes()) {
            String line = scopeChangeLine(change);
            if (line != null) {
                collector.info(line, null);
            }
        }
        for (RoundProgress row : classification.projectedProgress()) {
            collector.info("Projected %s: current round %s, last complete round %s, %d scheduled, %d played."
                            .formatted(scopeLabel(row.competition(), row.groupNumber(), row.phase()),
                                    row.currentRound() == null ? "-" : row.currentRound(),
                                    row.lastCompleteRound() == null ? "-" : row.lastCompleteRound(),
                                    row.scheduledMatches(), row.playedMatches()), null);
        }
        for (PreviewDuplicateFixtureId duplicate : classification.duplicateFixtureIds()) {
            collector.warning("Duplicate id_partido %s appears in %d file(s): %s.".formatted(
                    duplicate.sourceFixtureId(), duplicate.locations().size(),
                    String.join(", ", duplicate.locations())), null);
        }
        for (FixturePreview conflict : incremental.identityConflicts()) {
            collector.warning("Fixture identity conflict at %s: %s".formatted(
                    ActaPreviewValidationSupport.location(conflict.location()), conflict.reason()),
                    ActaPreviewValidationSupport.location(conflict.location()));
        }
    }

    /** A concise line for a scope with any non-UNCHANGED/PLAYED_KEPT change, else {@code null}. */
    private static String scopeChangeLine(PreviewScopeChanges change) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "new scheduled", change.newScheduled());
        addPart(parts, "new played", change.newPlayed());
        addPart(parts, "upgrade", change.upgrades());
        addPart(parts, "reschedule", change.reschedules());
        addPart(parts, "regression", change.regressions());
        addPart(parts, "invalid on played", change.invalidOnPlayed());
        addPart(parts, "identity conflict", change.identityConflicts());
        addPart(parts, "not stored", change.notStored());
        if (parts.isEmpty()) {
            return null;
        }
        return "Preview would change %s: %s.".formatted(
                scopeLabel(change.competition(), change.groupNumber(), change.phase()),
                String.join(", ", parts));
    }

    private static void addPart(List<String> parts, String label, long count) {
        if (count > 0) {
            parts.add("%d %s%s".formatted(count, label, count == 1 ? "" : "s"));
        }
    }

    private static String scopeLabel(String competition, Integer groupNumber, String phase) {
        StringBuilder label = new StringBuilder(competition == null ? "(unknown competition)" : competition);
        if (groupNumber != null) {
            label.append(" group ").append(groupNumber);
        }
        if (phase != null) {
            label.append(" phase ").append(phase);
        }
        return label.toString();
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

    /** The per-source traversal counters, normalized over the two summary shapes. */
    private record Traversal(long filesSeen, long dispatched, long skipped, long processorFailures) {
    }
}
