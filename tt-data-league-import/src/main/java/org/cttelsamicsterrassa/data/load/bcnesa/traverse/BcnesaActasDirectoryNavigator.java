package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Walks a BCNESA {@code actas-json} export and hands one {@link BcnesaMatchReportContext} per fixture
 * to a list of {@link BcnesaMatchReportProcessor}s.
 *
 * <p>The expected layout is</p>
 *
 * <pre>[baseFolder]/[season]/[league-competition]/[group]/[phase]/acta.json</pre>
 *
 * <p>Unlike the RFETM export, a BCNESA file is not one match: it is a whole matchday of one group, and
 * only its first fixture is named by {@code equipos}. Every group folder is therefore read twice -
 * once by {@link BcnesaClubIndex#build} to learn which club each licence plays for, and once here to
 * split each file into fixtures via {@link BcnesaMatchdaySplitter} and dispatch them. Which processors
 * run is a parameter of {@link #traverse(Path, List)}, so a caller can run a reporting pass and a
 * persisting pass over the same tree without changing anything here.</p>
 *
 * <h2>Report file names are opaque</h2>
 * <p>A report file is any {@code acta*.json} under a phase folder - {@code acta.json} or
 * {@code acta_<matchId>.json}, where the suffix is an arbitrary identifier carrying no meaning for the
 * import. The name is never parsed: the match day comes from the payload's {@code jornada} and the
 * clubs from {@code equipos} plus the group's licence index. Measured over the whole export,
 * {@code jornada} is present in all 2,996 files, so nothing is lost by ignoring the name.</p>
 *
 * <h2>Failure handling</h2>
 * <p>Nothing a single file or fixture can do aborts the run. Folders that do not fit the layout are
 * logged and skipped; so are payloads that fail to parse or that carry no {@code jornada}. A fixture
 * whose clubs cannot be attributed is logged and skipped rather than guessed at. Each processor call
 * is isolated, so a processor that throws neither stops the traversal nor prevents its peers from
 * seeing the same fixture. Every category is counted and reported in the returned
 * {@link BcnesaTraversalSummary}.</p>
 */
@Component
public class BcnesaActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaActasDirectoryNavigator.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("acta.*\\.json");
    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");
    private static final Pattern GROUP_FOLDER_PATTERN = Pattern.compile("G\\d+");

    private final List<BcnesaMatchReportProcessor> processors;
    private final ActaParser actaParser;
    private final BcnesaMatchdaySplitter splitter;

    public BcnesaActasDirectoryNavigator(List<BcnesaMatchReportProcessor> processors, ActaParser actaParser) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.actaParser = actaParser;
        this.splitter = new BcnesaMatchdaySplitter();
    }

    /**
     * Walks {@code baseFolder} and dispatches every readable fixture to {@code processors}. This is
     * the primary entry point: the processor list is explicit, and the injected list is ignored.
     */
    public BcnesaTraversalSummary traverse(Path baseFolder, List<BcnesaMatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season -> true, processors);
    }

    /**
     * Walks {@code baseFolder} and dispatches to the injected processors.
     */
    public BcnesaTraversalSummary traverse(Path baseFolder) throws IOException {
        return traverse(baseFolder, this.processors);
    }

    /**
     * Walks {@code baseFolder} and dispatches to the injected processors.
     */
    public BcnesaTraversalSummary traverse(String baseFolder) throws IOException {
        return traverse(Paths.get(baseFolder));
    }

    /**
     * Walks a single season of {@code baseFolder}.
     *
     * @param season season folder name, in {@code YYYY-YYYY} form
     */
    public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season,
                                                 List<BcnesaMatchReportProcessor> processors) throws IOException {
        return traverse(baseFolder, season::equals, processors);
    }

    /**
     * Walks a single season of {@code baseFolder}, dispatching to the injected processors.
     */
    public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season) throws IOException {
        return traverseSeason(baseFolder, season, this.processors);
    }

    private BcnesaTraversalSummary traverse(Path baseFolder,
                                            Predicate<String> seasonFilter,
                                            List<BcnesaMatchReportProcessor> processors) throws IOException {
        if (!Files.isDirectory(baseFolder)) {
            throw new IOException("Base folder is not a directory: " + baseFolder);
        }
        if (processors.isEmpty()) {
            LOGGER.warn("Traversing {} with no processors; nothing will be stored", baseFolder);
        }

        Counters counters = new Counters();
        LOGGER.info("Traversing BCNESA match reports under {}", baseFolder);

        for (Path seasonFolder : listDirectories(baseFolder)) {
            String season = seasonFolder.getFileName().toString();
            if (!SEASON_FOLDER_PATTERN.matcher(season).matches()) {
                LOGGER.warn("Skipping unexpected season folder {}", seasonFolder);
                continue;
            }
            if (!seasonFilter.test(season)) {
                LOGGER.debug("Skipping season {} (filtered out)", season);
                continue;
            }
            traverseSeasonFolder(seasonFolder, season, processors, counters);
        }

        BcnesaTraversalSummary summary = counters.toSummary();
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<BcnesaMatchReportProcessor> processors,
                                      Counters counters) throws IOException {
        for (Path competitionFolder : listDirectories(seasonFolder)) {
            String leagueCompetition = competitionFolder.getFileName().toString();
            for (Path groupFolder : listDirectories(competitionFolder)) {
                String group = groupFolder.getFileName().toString();
                if (!GROUP_FOLDER_PATTERN.matcher(group).matches()) {
                    LOGGER.warn("Skipping unexpected group folder {}", groupFolder);
                    continue;
                }
                BcnesaClubIndex clubIndex = BcnesaClubIndex.build(groupFolder, actaParser);
                for (Path phaseFolder : listDirectories(groupFolder)) {
                    String phase = phaseFolder.getFileName().toString();
                    traverseReportFolder(phaseFolder, season, leagueCompetition, group, phase, clubIndex,
                            processors, counters);
                }
            }
        }
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String leagueCompetition,
                                      String group,
                                      String phase,
                                      BcnesaClubIndex clubIndex,
                                      List<BcnesaMatchReportProcessor> processors,
                                      Counters counters) throws IOException {
        for (Path reportFile : listJsonFiles(reportFolder)) {
            counters.filesSeen++;

            Acta acta;
            try {
                acta = actaParser.parse(reportFile);
            } catch (ActaParseException e) {
                counters.filesSkipped++;
                LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                continue;
            }

            if (acta.round() == null) {
                counters.filesSkipped++;
                LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
                continue;
            }
            int round = acta.round();

            List<BcnesaMatchdaySplitter.Fixture> fixtures = splitter.split(acta, clubIndex);
            for (int i = 0; i < fixtures.size(); i++) {
                counters.fixturesSeen++;
                dispatchFixture(reportFile, season, leagueCompetition, group, phase, round, i, fixtures.get(i),
                        acta, processors, counters);
            }
        }
    }

    private void dispatchFixture(Path reportFile,
                                 String season,
                                 String leagueCompetition,
                                 String group,
                                 String phase,
                                 int round,
                                 int fixtureIndex,
                                 BcnesaMatchdaySplitter.Fixture fixture,
                                 Acta acta,
                                 List<BcnesaMatchReportProcessor> processors,
                                 Counters counters) {
        if (!fixture.isResolved()) {
            counters.fixturesUnresolved++;
            LOGGER.warn("Skipping fixture {} of {}: clubs could not be attributed ({} games)",
                    fixtureIndex, reportFile, fixture.games().size());
            return;
        }

        BcnesaMatchReportContext context = new BcnesaMatchReportContext(
                season, leagueCompetition, group, phase, round, fixtureIndex,
                fixture.homeClubName(), fixture.awayClubName(), reportFile, acta, fixture.games());
        dispatch(context, processors, counters);
    }

    private void dispatch(BcnesaMatchReportContext context,
                          List<BcnesaMatchReportProcessor> processors,
                          Counters counters) {
        counters.fixturesDispatched++;
        for (BcnesaMatchReportProcessor processor : processors) {
            try {
                processor.process(context);
            } catch (RuntimeException e) {
                counters.processorFailures++;
                LOGGER.error("Processor {} failed on fixture {} of {}",
                        processor.getClass().getSimpleName(), context.fixtureIndex(), context.matchReportFile(), e);
            }
        }
    }

    private List<Path> listDirectories(Path folder) throws IOException {
        return list(folder, Files::isDirectory);
    }

    private List<Path> listJsonFiles(Path folder) throws IOException {
        return list(folder, path -> Files.isRegularFile(path)
                && MATCH_REPORT_FILE_PATTERN.matcher(path.getFileName().toString()).matches());
    }

    private List<Path> list(Path folder, Predicate<Path> accepted) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                if (accepted.test(entry)) {
                    entries.add(entry);
                }
            }
        }
        entries.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return entries;
    }

    /** Mutable tally kept for the duration of one traversal. */
    private static final class Counters {
        private long filesSeen;
        private long filesSkipped;
        private long fixturesSeen;
        private long fixturesDispatched;
        private long fixturesUnresolved;
        private long processorFailures;

        private BcnesaTraversalSummary toSummary() {
            return new BcnesaTraversalSummary(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched,
                    fixturesUnresolved, processorFailures);
        }
    }
}
