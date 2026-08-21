package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerConsolidationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point of the league import.
 *
 * <p>Command-line options are defined by {@link ImportRuntimeCliContract} and
 * documented in the runtime README.</p>
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

    private final ClubConsolidationRunner clubConsolidationRunner;
    private final PlayerConsolidationRunner playerConsolidationRunner;
    private final Map<String, SourceDefinition> sourceDefinitions;
    private final RfetmClubConsolidationRunner rfetmClubConsolidationRunner;

    public App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator,
               FcttActasDirectoryNavigator fcttNavigator, ClubConsolidationRunner clubConsolidationRunner,
               PlayerConsolidationRunner playerConsolidationRunner) {
        this(rfetmNavigator, bcnesaNavigator, fcttNavigator, clubConsolidationRunner,
                playerConsolidationRunner, null);
    }

    @Autowired
    public App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator,
               FcttActasDirectoryNavigator fcttNavigator, ClubConsolidationRunner clubConsolidationRunner,
               PlayerConsolidationRunner playerConsolidationRunner,
               RfetmClubConsolidationRunner rfetmClubConsolidationRunner) {
        this.clubConsolidationRunner = clubConsolidationRunner;
        this.playerConsolidationRunner = playerConsolidationRunner;
        this.rfetmClubConsolidationRunner = rfetmClubConsolidationRunner;
        this.sourceDefinitions = Map.of(
                ImportRuntimeCliContract.SOURCE_RFETM,
                new SourceDefinition(ImportSource.RFETM,
                        (path, season) -> season == null
                                ? rfetmNavigator.traverse(path)
                                : rfetmNavigator.traverseSeason(path, season)),
                ImportRuntimeCliContract.SOURCE_BCNESA,
                new SourceDefinition(ImportSource.BCNESA,
                        (path, season) -> season == null
                                ? bcnesaNavigator.traverse(path)
                                : bcnesaNavigator.traverseSeason(path, season)),
                ImportRuntimeCliContract.SOURCE_FCTT,
                new SourceDefinition(ImportSource.FCTT,
                        (path, season) -> season == null
                                ? fcttNavigator.traverse(path)
                                : fcttNavigator.traverseSeason(path, season)));
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        ImportRuntimeArguments arguments = ImportRuntimeArguments.parse(args);
        String source = arguments.source();

        String actasFolder = arguments.actasFolder();
        if (actasFolder == null) {
            LOGGER.error("Missing required argument {}<path>", ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT);
            LOGGER.error("Usage: {}", ImportRuntimeCliContract.usage());
            throw new IllegalArgumentException("Missing required argument "
                    + ImportRuntimeCliContract.ACTAS_FOLDER_ARGUMENT + "<path>");
        }

        String season = arguments.optionalSeason().orElse(null);
        Path actasFolderPath = Path.of(actasFolder);
        ImportSource importSource = traverseSelectedSource(source, actasFolderPath, season);

        if (arguments.consolidateClubs()) {
            ClubConsolidationSummary consolidation = clubConsolidationRunner.run(importSource, arguments.consolidationMode());
            LOGGER.info("Club consolidation finished: {}", consolidation);
        }
        if (arguments.consolidatePlayers()) {
            PlayerConsolidationSummary consolidation = playerConsolidationRunner.run(importSource, arguments.playerConsolidationMode());
            LOGGER.info("Player consolidation finished: {}", consolidation);
        }
        if (arguments.consolidateRfetmClubs()) {
            String rfetmTeamsFolder = arguments.rfetmTeamsFolder();
            Path teamsFolderPath = Path.of(rfetmTeamsFolder);
            ClubConsolidationSummary consolidation = rfetmClubConsolidationRunner.run(
                    teamsFolderPath, season, arguments.rfetmClubConsolidationMode());
            LOGGER.info("RFETM club consolidation finished: {}", consolidation);
        }
    }

    private ImportSource traverseSelectedSource(String source, Path actasFolderPath, String season)
            throws IOException {
        SourceDefinition definition = sourceDefinitions.get(source);
        if (definition == null) {
            LOGGER.error("Unknown {}{}; expected {}",
                    ImportRuntimeCliContract.SOURCE_ARGUMENT,
                    source,
                    ImportRuntimeCliContract.supportedSourcesForMessage());
            throw new IllegalArgumentException("Unknown source: " + source);
        }

        Object summary = definition.traversal().traverse(actasFolderPath, season);
        LOGGER.info("{} import finished: {}", definition.source().name(), summary);
        return definition.source();
    }

    @FunctionalInterface
    private interface SourceTraversal {
        Object traverse(Path path, String season) throws IOException;
    }

    private record SourceDefinition(ImportSource source, SourceTraversal traversal) {
    }
}
