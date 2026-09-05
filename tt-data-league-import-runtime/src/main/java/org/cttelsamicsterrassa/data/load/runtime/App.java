package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionOptions;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionRequest;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionResult;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionService;
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

@SpringBootApplication(scanBasePackages = {"org.cttelsamicsterrassa"})
@EnableJpaRepositories(basePackages = "org.cttelsamicsterrassa")
@EntityScan(basePackages = "org.cttelsamicsterrassa")
public class App implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private final ImportExecutionService executionService;

    @Autowired
    public App(ImportExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Compatibility constructor retained for navigator dispatch tests and embedders.
     */
    App(RfetmActasDirectoryNavigator rfetm, BcnesaActasDirectoryNavigator bcnesa,
        FcttActasDirectoryNavigator fctt) {
        this.executionService = (request, options) -> {
            String season = request.season().map(Object::toString).orElse(null);
            try {
                return switch (request.source()) {
                    case RFETM -> {
                        TraversalSummary s = season == null ? rfetm.traverse(request.actasFolder())
                                : rfetm.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.dispatched(), s.skipped(),
                                s.processorFailures());
                    }
                    case BCNESA -> {
                        BcnesaTraversalSummary s = season == null ? bcnesa.traverse(request.actasFolder())
                                : bcnesa.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.fixturesDispatched(),
                                s.filesSkipped() + s.fixturesUnresolved(), s.processorFailures());
                    }
                    case FCTT -> {
                        TraversalSummary s = season == null ? fctt.traverse(request.actasFolder())
                                : fctt.traverseSeason(request.actasFolder(), season);
                        yield AppSupport.result(request, s.filesSeen(), s.dispatched(), s.skipped(),
                                s.processorFailures());
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
                arguments.rfetmTeamsFolder() == null ? null : Path.of(arguments.rfetmTeamsFolder()), 50);
        ImportExecutionResult result = executionService.execute(request, options);
        LOGGER.info("{} import finished: {}", source, result);
        if (result.status() == org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus.FAILURE) {
            throw new IllegalStateException("Import failed: " + result.issues());
        }
    }

    private static ImportSource parseSource(String source) {
        return switch (source) {
            case ImportRuntimeCliContract.SOURCE_RFETM -> ImportSource.RFETM;
            case ImportRuntimeCliContract.SOURCE_BCNESA -> ImportSource.BCNESA;
            case ImportRuntimeCliContract.SOURCE_FCTT -> ImportSource.FCTT;
            default -> throw new IllegalArgumentException("Unknown source: " + source);
        };
    }

    private static org.cttelsamicsterrassa.data.core.domain.shared.model.Season parseSeason(String value) {
        return org.cttelsamicsterrassa.data.core.domain.shared.model.Season.of(
                Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5, 9)));
    }

    private static final class AppSupport {
        private static ImportExecutionResult result(ImportExecutionRequest request, long files, long dispatched,
                                                    long skipped, long failures) {
            var status = failures > 0
                    ? org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus.FAILURE
                    : dispatched == 0
                    ? org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus.EMPTY_RESULT
                    : org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus.SUCCESS;
            return new ImportExecutionResult(request.source(), request.season().map(Object::toString), status,
                    new org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionMetrics(
                            files, dispatched, skipped, failures, 0, 0), java.util.List.of(), java.util.List.of());
        }
    }
}
