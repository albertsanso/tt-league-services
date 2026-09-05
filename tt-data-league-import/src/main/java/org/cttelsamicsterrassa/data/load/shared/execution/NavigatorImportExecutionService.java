package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubToCanonicalClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.TeamToClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerSeasonConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class NavigatorImportExecutionService implements ImportExecutionService {
    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;
    private final FcttActasDirectoryNavigator fcttNavigator;
    private final List<MatchContextProcessor> rfetmProcessors;
    private final List<BcnesaMatchReportProcessor> bcnesaProcessors;
    private final List<FcttMatchReportProcessor> fcttProcessors;
    private final TeamToClubConsolidationProcessor teamToClub;
    private final RfetmClubConsolidationProcessor rfetmClubs;
    private final FederatedClubToCanonicalClubConsolidationProcessor canonicalClubs;
    private final PlayerSeasonConsolidationProcessor players;

    @Autowired
    public NavigatorImportExecutionService(RfetmActasDirectoryNavigator rfetmNavigator,
                                           BcnesaActasDirectoryNavigator bcnesaNavigator,
                                           FcttActasDirectoryNavigator fcttNavigator,
                                           List<MatchContextProcessor> rfetmProcessors,
                                           List<BcnesaMatchReportProcessor> bcnesaProcessors,
                                           List<FcttMatchReportProcessor> fcttProcessors,
                                           TeamToClubConsolidationProcessor teamToClub,
                                           RfetmClubConsolidationProcessor rfetmClubs,
                                           FederatedClubToCanonicalClubConsolidationProcessor canonicalClubs,
                                           PlayerSeasonConsolidationProcessor players) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
        this.fcttNavigator = fcttNavigator;
        this.rfetmProcessors = rfetmProcessors == null ? List.of() : List.copyOf(rfetmProcessors);
        this.bcnesaProcessors = bcnesaProcessors == null ? List.of() : List.copyOf(bcnesaProcessors);
        this.fcttProcessors = fcttProcessors == null ? List.of() : List.copyOf(fcttProcessors);
        this.teamToClub = teamToClub;
        this.rfetmClubs = rfetmClubs;
        this.canonicalClubs = canonicalClubs;
        this.players = players;
    }

    public NavigatorImportExecutionService(RfetmActasDirectoryNavigator rfetmNavigator,
                                           BcnesaActasDirectoryNavigator bcnesaNavigator,
                                           FcttActasDirectoryNavigator fcttNavigator,
                                           List<MatchContextProcessor> rfetmProcessors,
                                           List<BcnesaMatchReportProcessor> bcnesaProcessors,
                                           List<FcttMatchReportProcessor> fcttProcessors) {
        this(rfetmNavigator, bcnesaNavigator, fcttNavigator, rfetmProcessors, bcnesaProcessors,
                fcttProcessors, null, null, null, null);
    }

    @Override
    public ImportExecutionResult execute(ImportExecutionRequest request, ImportExecutionOptions options) {
        Instant started = Instant.now();
        ImportExecutionOptions effective = options == null ? ImportExecutionOptions.defaults() : options;
        String season = request.season().map(Object::toString).orElse(null);
        List<ImportExecutionIssue> issues = new ArrayList<>();
        Counts counts;
        ImportRunContext runContext = new ImportRunContext(request.source(), season);
        try {
            counts = traverse(request.source(), request.actasFolder(), season, runContext);
        } catch (IOException | RuntimeException exception) {
            issues.add(new ImportExecutionIssue("traversal", request.actasFolder().toString(),
                    safeMessage(exception)));
            return result(request, ImportProcessStatus.FAILURE, countsZero(started), issues, List.of());
        }

        issues.addAll(counts.issues);
        ImportProcessStatus status = counts.processorFailures > 0 || !counts.issues.isEmpty()
                ? ImportProcessStatus.FAILURE
                : counts.dispatched == 0 ? ImportProcessStatus.EMPTY_RESULT : ImportProcessStatus.SUCCESS;
        List<PostProcessingOutcome> outcomes = new ArrayList<>();
        if (status == ImportProcessStatus.SUCCESS) {
            if (effective.clubConsolidationMode() != null) {
                outcomes.add(runClubs(request.source(), effective));
            }
            if (effective.playerConsolidationMode() != null) {
                outcomes.add(runPlayers(request.source(), effective.playerConsolidationMode()));
            }
            if (outcomes.stream().anyMatch(outcome -> !outcome.successful())) {
                status = ImportProcessStatus.FAILURE;
                outcomes.stream()
                        .filter(outcome -> !outcome.successful())
                        .flatMap(outcome -> outcome.errors().stream()
                                .map(error -> new ImportExecutionIssue(outcome.phase(), "", error)))
                        .forEach(issues::add);
            }
        }
        ImportExecutionMetrics metrics = new ImportExecutionMetrics(counts.files, counts.dispatched, counts.skipped,
                counts.processorFailures, 0, Duration.between(started, Instant.now()).toMillis());
        return new ImportExecutionResult(request.source(), request.season().map(Object::toString),
                status, metrics, issues, outcomes);
    }

    private Counts traverse(ImportSource source, Path folder, String season, ImportRunContext runContext) throws IOException {
        return switch (source) {
            case RFETM -> {
                TraversalSummary summary = season == null
                        ? rfetmNavigator.traverse(folder, rfetmProcessors, runContext)
                        : rfetmNavigator.traverseSeason(folder, season, rfetmProcessors, runContext);
                yield new Counts(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                        summary.processorFailures(), summary.issues());
            }
            case BCNESA -> {
                BcnesaTraversalSummary summary = season == null
                        ? bcnesaNavigator.traverse(folder, bcnesaProcessors, runContext)
                        : bcnesaNavigator.traverseSeason(folder, season, bcnesaProcessors, runContext);
                yield new Counts(summary.filesSeen(), summary.fixturesDispatched(),
                        summary.filesSkipped() + summary.fixturesUnresolved(), summary.processorFailures(),
                        summary.issues());
            }
            case FCTT -> {
                TraversalSummary summary = season == null
                        ? fcttNavigator.traverse(folder, fcttProcessors, runContext)
                        : fcttNavigator.traverseSeason(folder, season, fcttProcessors, runContext);
                yield new Counts(summary.filesSeen(), summary.dispatched(), summary.skipped(),
                        summary.processorFailures(), summary.issues());
            }
        };
    }

    private PostProcessingOutcome runClubs(ImportSource source, ImportExecutionOptions options) {
        Instant started = Instant.now();
        try {
            if (source == ImportSource.RFETM) {
                if (rfetmClubs == null || options.rfetmTeamsFolder() == null) {
                    return failureOutcome("clubs", options.clubConsolidationMode(), started,
                            "RFETM teams folder and consolidation processor are required");
                }
                ClubConsolidationSummary sourceSummary = rfetmClubs.process(
                        options.rfetmTeamsFolder(), options.clubConsolidationMode());
                return clubOutcome(sourceSummary, started);
            }
            if (teamToClub == null || canonicalClubs == null) {
                return failureOutcome("clubs", options.clubConsolidationMode(), started,
                        "Club consolidation processors are not configured");
            }
            ClubConsolidationSummary sourceSummary = teamToClub.consolidate(
                    source, options.clubConsolidationMode());
            ClubConsolidationSummary canonical = canonicalClubs.consolidate(source, options.clubConsolidationMode());
            return new PostProcessingOutcome("clubs", options.clubConsolidationMode(),
                    Duration.between(started, Instant.now()),
                    sourceSummary.scannedRegistrations() + canonical.scannedRegistrations(),
                    warnings(sourceSummary, canonical), errors(sourceSummary, canonical));
        } catch (RuntimeException exception) {
            return failureOutcome("clubs", options.clubConsolidationMode(), started, safeMessage(exception));
        }
    }

    private PostProcessingOutcome runPlayers(ImportSource source, ConsolidationMode mode) {
        Instant started = Instant.now();
        if (players == null) {
            return failureOutcome("players", mode, started, "Player consolidation processor is not configured");
        }
        try {
            PlayerConsolidationSummary summary = players.consolidate(source, mode);
            return new PostProcessingOutcome("players", mode, Duration.between(started, Instant.now()),
                    summary.scannedRegistrations(), summary.warnings().stream().map(Object::toString).toList(),
                    summary.errors().stream().map(Object::toString).toList());
        } catch (RuntimeException exception) {
            return failureOutcome("players", mode, started, safeMessage(exception));
        }
    }

    private static PostProcessingOutcome clubOutcome(ClubConsolidationSummary summary, Instant started) {
        return new PostProcessingOutcome("clubs", summary.mode(), Duration.between(started, Instant.now()),
                summary.scannedRegistrations(), summary.warnings().stream().map(Object::toString).toList(),
                summary.errors().stream().map(Object::toString).toList());
    }

    private static List<String> warnings(ClubConsolidationSummary first, ClubConsolidationSummary second) {
        return java.util.stream.Stream.concat(first.warnings().stream(), second.warnings().stream())
                .map(Object::toString).toList();
    }

    private static List<String> errors(ClubConsolidationSummary first, ClubConsolidationSummary second) {
        return java.util.stream.Stream.concat(first.errors().stream(), second.errors().stream())
                .map(Object::toString).toList();
    }

    private static PostProcessingOutcome failureOutcome(String phase, ConsolidationMode mode, Instant started,
                                                         String error) {
        return new PostProcessingOutcome(phase, mode, Duration.between(started, Instant.now()), 0, List.of(),
                List.of(error));
    }

    private static ImportExecutionResult result(ImportExecutionRequest request, ImportProcessStatus status,
                                                ImportExecutionMetrics metrics, List<ImportExecutionIssue> issues,
                                                List<PostProcessingOutcome> outcomes) {
        return new ImportExecutionResult(request.source(), request.season().map(Object::toString), status,
                metrics, issues, outcomes);
    }

    private static ImportExecutionMetrics countsZero(Instant started) {
        return new ImportExecutionMetrics(0, 0, 0, 0, 0, Duration.between(started, Instant.now()).toMillis());
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record Counts(long files, long dispatched, long skipped, long processorFailures,
                          List<ImportExecutionIssue> issues) {
    }
}
