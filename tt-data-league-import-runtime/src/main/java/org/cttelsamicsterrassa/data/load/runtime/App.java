package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillService;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillSummary;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication(scanBasePackages = {"org.cttelsamicsterrassa", "org.albertsanso.commons"})
@EnableJpaRepositories(basePackages = "org.cttelsamicsterrassa")
@EntityScan(basePackages = "org.cttelsamicsterrassa")
public class App implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private final ImportExecutionService executionService;
    private final ScheduledMatchBackfillService scheduledMatchBackfillService;

    @Autowired
    public App(ImportExecutionService executionService,
               ScheduledMatchBackfillService scheduledMatchBackfillService) {
        this.executionService = executionService;
        this.scheduledMatchBackfillService = scheduledMatchBackfillService;
    }

    /**
     * Compatibility constructor retained for navigator dispatch tests and embedders. The backfill is
     * not wired here; calling it throws.
     */
    App(RfetmActasDirectoryNavigator rfetm, BcnesaActasDirectoryNavigator bcnesa,
        FcttActasDirectoryNavigator fctt) {
        this.scheduledMatchBackfillService = new ScheduledMatchBackfillService(new ScheduledMatchBackfillRepository() {
            @Override
            public List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(
                    ImportSource source, Season season) {
                throw new IllegalStateException("backfill not wired");
            }

            @Override
            public ScheduledMatchBackfillWriteResult markScheduled(
                    ImportSource source, Season season, Collection<UUID> matchIds) {
                throw new IllegalStateException("backfill not wired");
            }
        });
        this.executionService = (request, options) -> {
            String season = request.season().map(Object::toString).orElse(null);
            try {
                return switch (request.source()) {
                    case RFETM -> {
                        TraversalSummary s = season == null ? rfetm.traverse(request.actasFolder())
                                : rfetm.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.dispatched(), s.skipped(),
                                s.processorFailures(), s.lifecycle());
                    }
                    case BCNESA -> {
                        BcnesaTraversalSummary s = season == null ? bcnesa.traverse(request.actasFolder())
                                : bcnesa.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.fixturesDispatched(),
                                s.filesSkipped() + s.fixturesUnresolved(), s.processorFailures(),
                                s.lifecycle());
                    }
                    case FCTT -> {
                        TraversalSummary s = season == null ? fctt.traverse(request.actasFolder())
                                : fctt.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.dispatched(), s.skipped(),
                                s.processorFailures(), s.lifecycle());
                    }
                };
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(args);

        if (arguments.backfillScheduledMatches()) {
            runBackfill(arguments);
            return;
        }

        if (arguments.actasFolder() == null) {
            LOGGER.error("Missing required argument {}<path>", ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT);
            throw new IllegalArgumentException("Missing required argument "
                    + ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT + "<path>");
        }
        ImportSource source = parseSource(arguments.source());
        ImportExecutionRequest request = new ImportExecutionRequest(source, Path.of(arguments.actasFolder()),
                arguments.optionalSeason().map(App::parseSeason));
        ImportExecutionOptions options = new ImportExecutionOptions(
                arguments.consolidateClubs() ? arguments.consolidationMode() : null,
                arguments.consolidatePlayers() ? arguments.playerConsolidationMode() : null,
                arguments.rfetmTeamsFolder() == null ? null : Path.of(arguments.rfetmTeamsFolder()), 50,
                arguments.amendedActaMode());
        ImportExecutionResult result = executionService.execute(request, options);
        LOGGER.info("{} import finished: {}", source, result);
        logRoundProgress(source, request.season(), result.roundProgress());
        if (result.status() == org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus.FAILURE) {
            throw new IllegalStateException("Import failed: " + result.issues());
        }
    }

    /**
     * FEAT-00084: one line per competition, group and phase so an operator can read the jornada state
     * straight from the run log. The progress is informational and never changes what was imported;
     * a run without {@code --season} computes nothing rather than guessing a season.
     */
    private static void logRoundProgress(ImportSource source, Optional<Season> season,
                                         List<RoundProgress> roundProgress) {
        if (season.isEmpty()) {
            LOGGER.info("{} round progress not computed: run without --season", source);
            return;
        }
        if (roundProgress.isEmpty()) {
            LOGGER.info("{}/{} round progress: no stored matches", source, season.get());
            return;
        }
        roundProgress.forEach(row -> LOGGER.info(
                "{}/{} progress {} G{} {}: current round {}, last complete round {}, scheduled {}, played {}",
                source, season.get(), dash(row.competition()), dash(row.groupNumber()), dash(row.phase()),
                dash(row.currentRound()), dash(row.lastCompleteRound()), row.scheduledMatches(),
                row.playedMatches()));
    }

    private static String dash(Object value) {
        return value == null ? "-" : value.toString();
    }

    /**
     * Runs the FEAT-00078 backfill instead of an import. Exclusive with every import argument, and
     * requires an explicit, strictly-formatted {@code --season}.
     */
    private void runBackfill(ImportRuntimeArguments arguments) {
        if (arguments.actasFolder() != null
                || arguments.rfetmTeamsFolder() != null
                || arguments.consolidateClubs()
                || arguments.consolidatePlayers()
                || arguments.amendedActaMode() != null) {
            throw new IllegalArgumentException(
                    ImportRuntimeCliContract.BACKFILL_SCHEDULED_MATCHES_ARGUMENT
                            + " cannot be combined with import arguments");
        }
        if (arguments.season() == null) {
            throw new IllegalArgumentException("Missing required argument "
                    + ImportRuntimeCliContract.SEASON_ARGUMENT + "<YYYY-YYYY> for "
                    + ImportRuntimeCliContract.BACKFILL_SCHEDULED_MATCHES_ARGUMENT);
        }

        ImportSource source = parseSource(arguments.source());
        Season season = parseStrictSeason(arguments.season());
        ScheduledMatchBackfillSummary summary =
                scheduledMatchBackfillService.run(source, season, arguments.backfillMode());
        LOGGER.info("{}/{} scheduled-match backfill finished in {} mode: {} candidates, {}",
                source, season, summary.mode(), summary.candidates().size(), summary.written());
    }

    private static ImportSource parseSource(String source) {
        return switch (source) {
            case ImportRuntimeCliContract.SOURCE_RFETM -> ImportSource.RFETM;
            case ImportRuntimeCliContract.SOURCE_BCNESA -> ImportSource.BCNESA;
            case ImportRuntimeCliContract.SOURCE_FCTT -> ImportSource.FCTT;
            default -> throw new IllegalArgumentException("Unknown source: " + source);
        };
    }

    private static Season parseSeason(String value) {
        return Season.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5, 9)));
    }

    /**
     * Stricter than {@link #parseSeason(String)}: requires the exact {@code YYYY-YYYY} form with
     * consecutive years. Applied only to the backfill path so the existing import season parsing
     * keeps accepting whatever it accepts today.
     */
    private static Season parseStrictSeason(String value) {
        if (!value.matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException(
                    "Invalid " + ImportRuntimeCliContract.SEASON_ARGUMENT + " value, expected YYYY-YYYY: " + value);
        }
        int start = Integer.parseInt(value.substring(0, 4));
        int end = Integer.parseInt(value.substring(5, 9));
        if (end != start + 1) {
            throw new IllegalArgumentException(
                    "A season must span exactly one consecutive year: " + value);
        }
        return Season.of(start, end);
    }

    private static final class AppSupport {
        private static ImportExecutionResult result(ImportExecutionRequest request, long files, long dispatched,
                                                    long skipped, long failures,
                                                    org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters lifecycle) {
            var status = org.cttelsamicsterrassa.data.load.shared.execution.ImportRunStatusPolicy.statusOf(
                    failures, false, dispatched, lifecycle);
            return new ImportExecutionResult(request.source(), request.season().map(Object::toString), status,
                    new org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionMetrics(
                            files, dispatched, skipped, failures, 0, 0, lifecycle),
                    java.util.List.of(), java.util.List.of());
        }
    }
}
