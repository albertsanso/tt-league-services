package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.club.ClubConsolidationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Path;

/**
 * Entry point of the league import.
 *
 * <pre>
 * --source=rfetm|bcnesa|fctt which export to read (optional; defaults to rfetm)
 * --base-folder=&lt;path&gt;      root of the actas-json export (required)
 * --season=&lt;YYYY-YYYY&gt;      import a single season (optional; all seasons when omitted)
 * --consolidate-clubs       after a successful traversal, repair source-scoped club associations
 * --consolidate-clubs=report
 *                           same matching path with no writes
 * </pre>
 *
 * <p>This module only wires and sequences: parsing lives in the parser, mapping in the processors,
 * and the walk in the navigator for the selected source. Club consolidation is opt-in and runs once
 * after the selected source traversal, over the whole source inventory.</p>
 */
@SpringBootApplication(scanBasePackages = {
        "org.cttelsamicsterrassa"
})
@EnableJpaRepositories(basePackages = "org.cttelsamicsterrassa")
@EntityScan(basePackages = "org.cttelsamicsterrassa")
public class App implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    private static final String SOURCE_ARGUMENT = "--source=";
    private static final String BASE_FOLDER_ARGUMENT = "--base-folder=";
    private static final String SOURCE_RFETM = "rfetm";
    private static final String SOURCE_BCNESA = "bcnesa";
    private static final String SOURCE_FCTT = "fctt";

    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;
    private final FcttActasDirectoryNavigator fcttNavigator;
    private final ClubConsolidationRunner clubConsolidationRunner;

    public App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator,
               FcttActasDirectoryNavigator fcttNavigator, ClubConsolidationRunner clubConsolidationRunner) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
        this.fcttNavigator = fcttNavigator;
        this.clubConsolidationRunner = clubConsolidationRunner;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(args);
        String source = arguments.source();

        String baseFolder = arguments.baseFolder();
        if (baseFolder == null) {
            LOGGER.error("Missing required argument {}<path>", BASE_FOLDER_ARGUMENT);
            LOGGER.error("Usage: --source=rfetm|bcnesa|fctt --base-folder=<path> [--season=<YYYY-YYYY>] [--consolidate-clubs[=report]]");
            throw new IllegalArgumentException("Missing required argument " + BASE_FOLDER_ARGUMENT + "<path>");
        }

        String season = arguments.optionalSeason().orElse(null);
        Path base = Path.of(baseFolder);
        ImportSource importSource;

        switch (source) {
            case SOURCE_RFETM -> {
                var summary = season == null ? rfetmNavigator.traverse(base) : rfetmNavigator.traverseSeason(base, season);
                LOGGER.info("RFETM import finished: {}", summary);
                importSource = ImportSource.RFETM;
            }
            case SOURCE_BCNESA -> {
                var summary = season == null ? bcnesaNavigator.traverse(base) : bcnesaNavigator.traverseSeason(base, season);
                LOGGER.info("BCNESA import finished: {}", summary);
                importSource = ImportSource.BCNESA;
            }
            case SOURCE_FCTT -> {
                var summary = season == null ? fcttNavigator.traverse(base) : fcttNavigator.traverseSeason(base, season);
                LOGGER.info("FCTT import finished: {}", summary);
                importSource = ImportSource.FCTT;
            }
            default -> {
                LOGGER.error("Unknown {}{}; expected \"{}\", \"{}\", or \"{}\"",
                        SOURCE_ARGUMENT, source, SOURCE_RFETM, SOURCE_BCNESA, SOURCE_FCTT);
                throw new IllegalArgumentException("Unknown source: " + source);
            }
        }

        if (arguments.consolidateClubs()) {
            ClubConsolidationSummary consolidation = clubConsolidationRunner.run(importSource, arguments.consolidationMode());
            LOGGER.info("Club consolidation finished: {}", consolidation);
        }
    }
}
