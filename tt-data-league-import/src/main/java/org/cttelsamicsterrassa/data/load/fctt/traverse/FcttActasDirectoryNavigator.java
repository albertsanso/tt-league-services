package org.cttelsamicsterrassa.data.load.fctt.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebContextFactory;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebTraversalCounters;
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
 * {@code [baseFolder]/[season]/[male|female]/[league-competition]/[group]/jornada-[day]-partido-[match].json}.
 * Directory names provide the contextual identity, while {@code jornada} in the parsed payload is
 * the sole source of the round. A competition folder without a group subfolder dispatches its report
 * files directly, with a {@code null} group. Filename suffixes are opaque.</p>
 *
 * <p>The layout written by {@code tt-league-ingest} is read by the shared
 * {@link FcttWebActasDirectoryNavigator} after the legacy pass of each season:
 * {@code [baseFolder]/[season]/[category]/[g<n>]/[phase]/jornada_[day]_local_team_[id]_away_team_[id].json}.
 * There the competition is the category folder, the gender comes from the payload's {@code genero}
 * ({@code masculino}/{@code femenino}), the category folder is mapped to the legacy competition name
 * where one exists ({@code tdm} to {@code tercera-nacional}), the phase folder is informational (the payload {@code fase}
 * wins) and a group folder that is not {@code g<n>} dispatches with a {@code null} group. This navigator
 * keeps only the legacy {@code male}/{@code female} layout; both passes share one set of counters.</p>
 */
@Component
public class FcttActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttActasDirectoryNavigator.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("jornada-\\d+-partido-[\\d-]+\\.json");
    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");
    private static final FcttWebContextFactory<FcttMatchReportContext> FCTT_CONTEXTS = report ->
            new FcttMatchReportContext(report.season(), report.gender(), report.competition(), report.group(),
                    report.round(), report.file(), report.acta(), report.runContext());

    private final List<FcttMatchReportProcessor> processors;
    private final ActaParser actaParser;
    private final FcttWebActasDirectoryNavigator sharedNavigator;

    public FcttActasDirectoryNavigator(List<FcttMatchReportProcessor> processors, ActaParser actaParser) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.actaParser = actaParser;
        this.sharedNavigator = new FcttWebActasDirectoryNavigator(ImportSource.FCTT, actaParser);
    }

    /**
     * Walks {@code baseFolder} and dispatches every readable report to the explicit processor list.
     */
    public TraversalSummary traverse(Path baseFolder, List<FcttMatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season -> true, processors, new ImportRunContext(
                ImportSource.FCTT, null),
                ImportProgressListener.noop());
    }
    public TraversalSummary traverse(Path baseFolder, List<FcttMatchReportProcessor> processors,
                                     ImportRunContext context) throws IOException {
        return traverse(baseFolder, season -> true, processors, context, ImportProgressListener.noop());
    }

    /**
     * As {@link #traverse(Path, List, ImportRunContext)}, additionally reporting file-level progress
     * while the traversal runs.
     */
    public TraversalSummary traverse(Path baseFolder, List<FcttMatchReportProcessor> processors,
                                     ImportRunContext context, ImportProgressListener progressListener)
            throws IOException {
        return traverse(baseFolder, season -> true, processors, context, progressListener);
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
                ImportSource.FCTT, season),
                ImportProgressListener.noop());
    }
    public TraversalSummary traverseSeason(Path baseFolder, String season, List<FcttMatchReportProcessor> processors,
                                           ImportRunContext context) throws IOException {
        return traverse(baseFolder, season::equals, processors, context, ImportProgressListener.noop());
    }

    /**
     * As {@link #traverseSeason(Path, String, List, ImportRunContext)}, additionally reporting
     * file-level progress while the traversal runs.
     */
    public TraversalSummary traverseSeason(Path baseFolder, String season, List<FcttMatchReportProcessor> processors,
                                           ImportRunContext context, ImportProgressListener progressListener)
            throws IOException {
        return traverse(baseFolder, season::equals, processors, context, progressListener);
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
                                      ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        if (!Files.isDirectory(baseFolder)) {
            throw new IOException("Base folder is not a directory: " + baseFolder);
        }
        if (processors.isEmpty()) {
            LOGGER.warn("Traversing {} with no processors; nothing will be stored", baseFolder);
        }

        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        counters.total(countReportFiles(baseFolder, seasonFilter));
        progressListener.onProgress(counters.progress());
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
            traverseSeasonFolder(seasonFolder, season, processors, counters, runContext, progressListener);
        }

        TraversalSummary summary = toSummary(counters, runContext.lifecycleCounters());
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<FcttMatchReportProcessor> processors,
                                      FcttWebTraversalCounters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path genderFolder : listDirectories(seasonFolder)) {
            String gender = genderFolder.getFileName().toString();
            if (!FcttWebActasDirectoryNavigator.isFcttLegacyGenderFolder(gender)) {
                continue;
            }
            for (Path competitionFolder : listDirectories(genderFolder)) {
                String leagueCompetition = competitionFolder.getFileName().toString();
                traverseCompetitionFolder(competitionFolder, season, gender, leagueCompetition, processors,
                        counters, runContext, progressListener);
            }
        }
        sharedNavigator.traverseSeason(seasonFolder, season, runContext, counters, progressListener,
                FCTT_CONTEXTS, context -> dispatch(context, processors, counters));
    }

    private void traverseCompetitionFolder(Path competitionFolder,
                                           String season,
                                           String gender,
                                           String leagueCompetition,
                                           List<FcttMatchReportProcessor> processors,
                                           FcttWebTraversalCounters counters, ImportRunContext runContext,
                                           ImportProgressListener progressListener) throws IOException {
        traverseReportFolder(competitionFolder, season, gender, leagueCompetition, null, processors, counters,
                runContext, progressListener);
        for (Path groupFolder : listDirectories(competitionFolder)) {
            String group = groupFolder.getFileName().toString();
            traverseReportFolder(groupFolder, season, gender, leagueCompetition, group, processors, counters,
                    runContext, progressListener);
        }
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String gender,
                                      String leagueCompetition,
                                      String group,
                                      List<FcttMatchReportProcessor> processors,
                                      FcttWebTraversalCounters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path reportFile : listMatchReportFiles(reportFolder)) {
            counters.fileSeen();
            sharedNavigator.handleFcttReport(reportFile, season, gender, leagueCompetition, group, FCTT_CONTEXTS,
                    context -> dispatch(context, processors, counters), runContext, counters, progressListener);
        }
    }

    /**
     * Counts the report files a traversal will visit, using the same folder/file predicates as
     * {@link #traverseSeasonFolder} without parsing anything, so progress can report a real total
     * from the first callback instead of discovering it only once traversal finishes.
     */
    private long countReportFiles(Path baseFolder, Predicate<String> seasonFilter) throws IOException {
        long total = 0;
        for (Path seasonFolder : listDirectories(baseFolder)) {
            String season = seasonFolder.getFileName().toString();
            if (!SEASON_FOLDER_PATTERN.matcher(season).matches() || !seasonFilter.test(season)) {
                continue;
            }
            total += sharedNavigator.countReportFiles(seasonFolder);
            for (Path genderFolder : listDirectories(seasonFolder)) {
                if (!FcttWebActasDirectoryNavigator.isFcttLegacyGenderFolder(genderFolder.getFileName().toString())) {
                    continue;
                }
                for (Path competitionFolder : listDirectories(genderFolder)) {
                    total += listMatchReportFiles(competitionFolder).size();
                    for (Path groupFolder : listDirectories(competitionFolder)) {
                        total += listMatchReportFiles(groupFolder).size();
                    }
                }
            }
        }
        return total;
    }

    private void dispatch(FcttMatchReportContext context,
                          List<FcttMatchReportProcessor> processors,
                          FcttWebTraversalCounters counters) {
        counters.fixtureDispatched();
        for (FcttMatchReportProcessor processor : processors) {
            try {
                processor.process(context);
            } catch (RuntimeException e) {
                counters.processorFailed();
                counters.issue(new ImportExecutionIssue(processor.getClass().getSimpleName(),
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

    private static TraversalSummary toSummary(FcttWebTraversalCounters counters, ImportLifecycleCounters lifecycle) {
        return new TraversalSummary(counters.filesSeen(), counters.fixturesDispatched(), counters.filesSkipped(),
                counters.processorFailures(), counters.issues(), lifecycle);
    }
}
