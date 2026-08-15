package org.cttelsamicsterrassa.data.load.runtime;

import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.rfetm.traverse.RfetmActasDirectoryNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Entry point of the league import.
 *
 * <pre>
 * --source=rfetm|bcnesa     which export to read (optional; defaults to rfetm)
 * --base-folder=&lt;path&gt;      root of the actas-json export (required)
 * --season=&lt;YYYY-YYYY&gt;      import a single season (optional; all seasons when omitted)
 * </pre>
 *
 * <p>This module only wires and sequences: parsing lives in the parser, mapping in the processors,
 * and the walk in the navigator for the selected source.</p>
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
    private static final String SEASON_ARGUMENT = "--season=";
    private static final String SOURCE_RFETM = "rfetm";
    private static final String SOURCE_BCNESA = "bcnesa";

    private final RfetmActasDirectoryNavigator rfetmNavigator;
    private final BcnesaActasDirectoryNavigator bcnesaNavigator;

    public App(RfetmActasDirectoryNavigator rfetmNavigator, BcnesaActasDirectoryNavigator bcnesaNavigator) {
        this.rfetmNavigator = rfetmNavigator;
        this.bcnesaNavigator = bcnesaNavigator;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String source = valueOf(args, SOURCE_ARGUMENT);
        source = source == null ? SOURCE_RFETM : source.toLowerCase(Locale.ROOT);

        String baseFolder = valueOf(args, BASE_FOLDER_ARGUMENT);
        if (baseFolder == null) {
            LOGGER.error("Missing required argument {}<path>", BASE_FOLDER_ARGUMENT);
            LOGGER.error("Usage: --source=rfetm|bcnesa --base-folder=<path> [--season=<YYYY-YYYY>]");
            throw new IllegalArgumentException("Missing required argument " + BASE_FOLDER_ARGUMENT + "<path>");
        }

        String season = valueOf(args, SEASON_ARGUMENT);
        Path base = Path.of(baseFolder);

        switch (source) {
            case SOURCE_RFETM -> {
                var summary = season == null ? rfetmNavigator.traverse(base) : rfetmNavigator.traverseSeason(base, season);
                LOGGER.info("RFETM import finished: {}", summary);
            }
            case SOURCE_BCNESA -> {
                var summary = season == null ? bcnesaNavigator.traverse(base) : bcnesaNavigator.traverseSeason(base, season);
                LOGGER.info("BCNESA import finished: {}", summary);
            }
            default -> {
                LOGGER.error("Unknown {}{}; expected \"{}\" or \"{}\"", SOURCE_ARGUMENT, source, SOURCE_RFETM, SOURCE_BCNESA);
                throw new IllegalArgumentException("Unknown source: " + source);
            }
        }
    }

    private static String valueOf(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
