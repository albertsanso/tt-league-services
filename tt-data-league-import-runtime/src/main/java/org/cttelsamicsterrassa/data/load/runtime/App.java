package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaTraversalSummary;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.TeamToClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubToCanonicalClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerSeasonConsolidationProcessor;
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

    private final Map<String, SourceDefinition> sourceDefinitions;
    private final TeamToClubConsolidationProcessor teamToClubConsolidationProcessor;
    private final PlayerSeasonConsolidationProcessor playerSeasonConsolidationProcessor;
    private final RfetmClubConsolidationProcessor rfetmClubConsolidationProcessor;
    private final FederatedClubToCanonicalClubConsolidationProcessor federatedClubToCanonicalClubConsolidationProcessor;

    @Autowired
    public App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator,
               FcttActasDirectoryNavigator fcttNavigator,
               TeamToClubConsolidationProcessor teamToClubConsolidationProcessor,
               PlayerSeasonConsolidationProcessor playerSeasonConsolidationProcessor,
               RfetmClubConsolidationProcessor rfetmClubConsolidationProcessor,
               FederatedClubToCanonicalClubConsolidationProcessor federatedClubToCanonicalClubConsolidationProcessor) {
        this.sourceDefinitions = sourceDefinitions(rfetmNavigator, bcnesaNavigator, fcttNavigator);
        this.teamToClubConsolidationProcessor = teamToClubConsolidationProcessor;
        this.playerSeasonConsolidationProcessor = playerSeasonConsolidationProcessor;
        this.rfetmClubConsolidationProcessor = rfetmClubConsolidationProcessor;
        this.federatedClubToCanonicalClubConsolidationProcessor = federatedClubToCanonicalClubConsolidationProcessor;
    }

    App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator,
        FcttActasDirectoryNavigator fcttNavigator) {
        this.sourceDefinitions = sourceDefinitions(rfetmNavigator, bcnesaNavigator, fcttNavigator);
        this.teamToClubConsolidationProcessor = null;
        this.playerSeasonConsolidationProcessor = null;
        this.rfetmClubConsolidationProcessor = null;
        this.federatedClubToCanonicalClubConsolidationProcessor = null;
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
        runRequestedConsolidations(importSource, arguments);

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
        ensureSuccessfulTraversal(summary);
        LOGGER.info("{} import finished: {}", definition.source().name(), summary);
        return definition.source();
    }

    private static void ensureSuccessfulTraversal(Object summary) {
        long processorFailures = switch (summary) {
            case TraversalSummary traversalSummary -> traversalSummary.processorFailures();
            case BcnesaTraversalSummary bcnesaSummary -> bcnesaSummary.processorFailures();
            default -> 0;
        };
        if (processorFailures > 0) {
            throw new IllegalStateException(
                    "Import traversal completed with " + processorFailures + " processor failures");
        }
    }

    private void runRequestedConsolidations(ImportSource source, ImportRuntimeArguments arguments) {
        if (arguments.consolidateClubs()) {
            ClubConsolidationSummary clubSummary = runClubConsolidation(source, arguments);
            LOGGER.info("Club consolidation finished for {}: {}", source, clubSummary);
        }
        if (arguments.consolidatePlayers()) {
            if (playerSeasonConsolidationProcessor == null) {
                throw new IllegalStateException("PlayerSeasonConsolidationProcessor is not configured");
            }
            PlayerConsolidationSummary summary = playerSeasonConsolidationProcessor.consolidate(
                    source,
                    arguments.playerConsolidationMode());
            LOGGER.info("Player consolidation finished for {}: {}", source, summary);
        }
    }

    private ClubConsolidationSummary runClubConsolidation(ImportSource source, ImportRuntimeArguments arguments) {
        ConsolidationMode mode = arguments.consolidationMode();
        if (source == ImportSource.RFETM) {
            if (rfetmClubConsolidationProcessor == null) {
                throw new IllegalStateException("RfetmClubConsolidationProcessor is not configured");
            }
            if (arguments.rfetmTeamsFolder() == null) {
                throw new IllegalArgumentException("Missing required argument "
                        + ImportRuntimeCliContract.RFETM_TEAMS_FOLDER_ARGUMENT
                        + "<path> when consolidating RFETM clubs");
            }
            return combineClubSummaries(
                    rfetmClubConsolidationProcessor.process(Path.of(arguments.rfetmTeamsFolder()), mode),
                    runCanonicalClubConsolidation(source, mode));
        }
        if (teamToClubConsolidationProcessor == null) {
            throw new IllegalStateException("TeamToClubConsolidationProcessor is not configured");
        }
        return combineClubSummaries(
                teamToClubConsolidationProcessor.consolidate(source, mode),
                runCanonicalClubConsolidation(source, mode));
    }

    private ClubConsolidationSummary runCanonicalClubConsolidation(ImportSource source, ConsolidationMode mode) {
        if (federatedClubToCanonicalClubConsolidationProcessor == null) {
            throw new IllegalStateException(
                    "FederatedClubToCanonicalClubConsolidationProcessor is not configured");
        }
        return federatedClubToCanonicalClubConsolidationProcessor.consolidate(source, mode);
    }

    private static ClubConsolidationSummary combineClubSummaries(
            ClubConsolidationSummary sourceSummary,
            ClubConsolidationSummary canonicalSummary) {
        return new ClubConsolidationSummary(
                sourceSummary.source(),
                sourceSummary.mode(),
                canonicalSummary.scannedRegistrations(),
                sourceSummary.exactGroups() + canonicalSummary.exactGroups(),
                sourceSummary.acceptedFuzzyGroups() + canonicalSummary.acceptedFuzzyGroups(),
                sourceSummary.clubsCreated() + canonicalSummary.clubsCreated(),
                canonicalSummary.canonicalLinksCreated(),
                sourceSummary.registrationsReassociated(),
                canonicalSummary.alreadyCorrectRegistrations(),
                java.util.stream.Stream.concat(sourceSummary.consolidations().stream(),
                                canonicalSummary.consolidations().stream())
                        .toList(),
                java.util.stream.Stream.concat(sourceSummary.warnings().stream(),
                                canonicalSummary.warnings().stream())
                        .toList(),
                java.util.stream.Stream.concat(sourceSummary.errors().stream(),
                                canonicalSummary.errors().stream())
                        .toList());
    }

    @FunctionalInterface
    private interface SourceTraversal {
        Object traverse(Path path, String season) throws IOException;
    }

    private record SourceDefinition(ImportSource source, SourceTraversal traversal) {
    }

    private static Map<String, SourceDefinition> sourceDefinitions(
            RfetmActasDirectoryNavigator rfetmNavigator,
            BcnesaActasDirectoryNavigator bcnesaNavigator,
            FcttActasDirectoryNavigator fcttNavigator) {
        return Map.of(
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
}
