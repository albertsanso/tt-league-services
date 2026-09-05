package org.cttelsamicsterrassa.data.load.fctt.traverse;

import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
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
 * Walks an FCTT {@code actas-json} export and dispatches one context per match report.
 *
 * <p>The expected layout is
 * {@code [baseFolder]/[season]/[league-competition]/[group]/jornada_[day]_partido_[match].json}.
 * Directory names provide the contextual identity, while {@code jornada} in the parsed payload is
 * the sole source of the round. Filename suffixes are opaque.</p>
 */
@Component
public class FcttActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttActasDirectoryNavigator.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("jornada_.*_partido_.*\\.json");
    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");

    private final List<FcttMatchReportProcessor> processors;
    private final ActaParser actaParser;

    public FcttActasDirectoryNavigator(List<FcttMatchReportProcessor> processors, ActaParser actaParser) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.actaParser = actaParser;
    }

    /**
     * Walks {@code baseFolder} and dispatches every readable report to the explicit processor list.
     */
    public TraversalSummary traverse(Path baseFolder, List<FcttMatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season -> true, processors, new ImportRunContext(
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, null));
    }
    public TraversalSummary traverse(Path baseFolder, List<FcttMatchReportProcessor> processors,
                                     ImportRunContext context) throws IOException {
        return traverse(baseFolder, season -> true, processors, context);
    }

    /**
     * Walks {@code baseFolder} and dispatches to injected processors.
     */
    public TraversalSummary traverse(Path baseFolder) throws IOException {
        return traverse(baseFolder, processors);
    }

    /**
     * Walks {@code baseFolder} and dispatches to injected processors.
     */
    public TraversalSummary traverse(String baseFolder) throws IOException {
        return traverse(Paths.get(baseFolder));
    }

    /**
     * Walks one season of {@code baseFolder} and dispatches to the explicit processor list.
     */
    public TraversalSummary traverseSeason(Path baseFolder, String season, List<FcttMatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season::equals, processors, new ImportRunContext(
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, season));
    }
    public TraversalSummary traverseSeason(Path baseFolder, String season, List<FcttMatchReportProcessor> processors,
                                           ImportRunContext context) throws IOException {
        return traverse(baseFolder, season::equals, processors, context);
    }

    /**
     * Walks one season of {@code baseFolder} and dispatches to injected processors.
     */
    public TraversalSummary traverseSeason(Path baseFolder, String season) throws IOException {
        return traverseSeason(baseFolder, season, processors);
    }

    private TraversalSummary traverse(Path baseFolder,
                                      Predicate<String> seasonFilter,
                                      List<FcttMatchReportProcessor> processors,
                                      ImportRunContext runContext) throws IOException {
        if (!Files.isDirectory(baseFolder)) {
            throw new IOException("Base folder is not a directory: " + baseFolder);
        }
        if (processors.isEmpty()) {
            LOGGER.warn("Traversing {} with no processors; nothing will be stored", baseFolder);
        }

        Counters counters = new Counters();
        LOGGER.info("Traversing FCTT match reports under {}", baseFolder);

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
            traverseSeasonFolder(seasonFolder, season, processors, counters, runContext);
        }

        TraversalSummary summary = counters.toSummary();
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<FcttMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext) throws IOException {
        for (Path competitionFolder : listDirectories(seasonFolder)) {
            String leagueCompetition = competitionFolder.getFileName().toString();
            for (Path groupFolder : listDirectories(competitionFolder)) {
                String group = groupFolder.getFileName().toString();
                traverseReportFolder(groupFolder, season, leagueCompetition, group, processors, counters, runContext);
            }
        }
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String leagueCompetition,
                                      String group,
                                      List<FcttMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext) throws IOException {
        for (Path reportFile : listMatchReportFiles(reportFolder)) {
            counters.filesSeen++;

            Acta acta;
            try {
                acta = actaParser.parse(reportFile);
            } catch (ActaParseException e) {
                counters.skipped++;
                LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                continue;
            }

            if (acta.round() == null) {
                counters.skipped++;
                LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
                continue;
            }

            FcttMatchReportContext context = new FcttMatchReportContext(
                    season, leagueCompetition, group, acta.round(), reportFile, acta, runContext);
            if (context.groupNumber().isEmpty()) {
                counters.skipped++;
                LOGGER.warn("Skipping {}: group folder \"{}\" is not G<number> or <number>",
                        reportFile, group);
                continue;
            }
            dispatch(context, processors, counters);
        }
    }

    private void dispatch(FcttMatchReportContext context,
                          List<FcttMatchReportProcessor> processors,
                          Counters counters) {
        counters.dispatched++;
        for (FcttMatchReportProcessor processor : processors) {
            try {
                processor.process(context);
            } catch (RuntimeException e) {
                counters.processorFailures++;
                counters.issues.add(new ImportExecutionIssue(processor.getClass().getSimpleName(),
                        context.matchReportFile().toString(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                LOGGER.error("Processor {} failed on {}",
                        processor.getClass().getSimpleName(), context.matchReportFile(), e);
            }
        }
    }

    private List<Path> listDirectories(Path folder) throws IOException {
        return list(folder, Files::isDirectory);
    }

    private List<Path> listMatchReportFiles(Path folder) throws IOException {
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
        private long dispatched;
        private long skipped;
        private long processorFailures;
        private final List<ImportExecutionIssue> issues = new ArrayList<>();

        private TraversalSummary toSummary() {
            return new TraversalSummary(filesSeen, dispatched, skipped, processorFailures, issues);
        }
    }
}
