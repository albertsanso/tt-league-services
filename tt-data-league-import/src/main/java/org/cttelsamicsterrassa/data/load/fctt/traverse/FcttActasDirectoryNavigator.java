package org.cttelsamicsterrassa.data.load.fctt.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
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
import java.util.Map;
import java.util.Set;
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
 * <p>The layout written by {@code tt-league-ingest} is also accepted:
 * {@code [baseFolder]/[season]/[category]/[g<n>]/[phase]/jornada_[day]_local_team_[id]_away_team_[id].json}.
 * There the competition is the category folder, the gender comes from the payload's {@code genero}
 * ({@code masculino}/{@code femenino}), the category folder is mapped to the legacy competition name
 * where one exists ({@code tdm} to {@code tercera-nacional}), the phase folder is informational (the payload {@code fase}
 * wins) and a group folder that is not {@code g<n>} dispatches with a {@code null} group.</p>
 */
@Component
public class FcttActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttActasDirectoryNavigator.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("jornada-\\d+-partido-[\\d-]+\\.json");
    private static final Pattern INGEST_REPORT_FILE_PATTERN =
            Pattern.compile("jornada_\\d+_local_team_[^_]+_away_team_[^_]+\\.json");
    private static final Pattern INGEST_GROUP_FOLDER_PATTERN = Pattern.compile("(?i)g\\d+");
    /**
     * Ingest category folders whose stored competition keeps the name of the legacy layout, so both
     * layouts feed the same competition. A category without an entry keeps its own folder name.
     */
    private static final Map<String, String> INGEST_CATEGORY_ALIASES = Map.of("tdm", "tercera-nacional");
    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");
    private static final Set<String> GENDER_FOLDERS = Set.of("male", "female");

    private final List<FcttMatchReportProcessor> processors;
    private final ActaParser actaParser;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();

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
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, null),
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
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT, season),
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

        Counters counters = new Counters();
        counters.total = countReportFiles(baseFolder, seasonFilter);
        progressListener.onProgress(ImportRunProgress.determinate(0, counters.total, 0, 0));
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

        TraversalSummary summary = counters.toSummary(runContext.lifecycleCounters());
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<FcttMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path genderFolder : listDirectories(seasonFolder)) {
            String gender = genderFolder.getFileName().toString();
            if (!GENDER_FOLDERS.contains(gender)) {
                traverseIngestCategoryFolder(genderFolder, season, processors, counters, runContext,
                        progressListener);
                continue;
            }
            for (Path competitionFolder : listDirectories(genderFolder)) {
                String leagueCompetition = competitionFolder.getFileName().toString();
                traverseCompetitionFolder(competitionFolder, season, gender, leagueCompetition, processors,
                        counters, runContext, progressListener);
            }
        }
    }

    private void traverseCompetitionFolder(Path competitionFolder,
                                           String season,
                                           String gender,
                                           String leagueCompetition,
                                           List<FcttMatchReportProcessor> processors,
                                           Counters counters, ImportRunContext runContext,
                                           ImportProgressListener progressListener) throws IOException {
        traverseReportFolder(competitionFolder, season, gender, leagueCompetition, null, processors, counters,
                runContext, progressListener);
        for (Path groupFolder : listDirectories(competitionFolder)) {
            String group = groupFolder.getFileName().toString();
            traverseReportFolder(groupFolder, season, gender, leagueCompetition, group, processors, counters,
                    runContext, progressListener);
        }
    }

    private void traverseIngestCategoryFolder(Path categoryFolder, String season,
                                              List<FcttMatchReportProcessor> processors,
                                              Counters counters, ImportRunContext runContext,
                                              ImportProgressListener progressListener) throws IOException {
        String folderName = categoryFolder.getFileName().toString();
        String category = INGEST_CATEGORY_ALIASES.getOrDefault(folderName, folderName);
        for (Path groupFolder : listDirectories(categoryFolder)) {
            String groupName = groupFolder.getFileName().toString();
            String group = INGEST_GROUP_FOLDER_PATTERN.matcher(groupName).matches() ? groupName : null;
            for (Path phaseFolder : listDirectories(groupFolder)) {
                for (Path reportFile : list(phaseFolder, FcttActasDirectoryNavigator::isIngestReportFile)) {
                    counters.filesSeen++;
                    Acta acta;
                    try {
                        acta = actaParser.parse(reportFile);
                    } catch (ActaParseException e) {
                        counters.skipped++;
                        LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                        reportProgress(counters, progressListener);
                        continue;
                    }
                    String gender = genderOf(acta.gender());
                    if (gender == null) {
                        counters.processorFailures++;
                        counters.issues.add(new ImportExecutionIssue("FcttActasDirectoryNavigator",
                                reportFile.toString(),
                                "payload genero \"" + acta.gender() + "\" is not masculino or femenino"));
                        reportProgress(counters, progressListener);
                        continue;
                    }
                    dispatchReport(reportFile, acta, season, gender, category, group, processors, counters,
                            runContext, progressListener);
                }
            }
        }
    }

    private static boolean isIngestReportFile(Path path) {
        return Files.isRegularFile(path) && INGEST_REPORT_FILE_PATTERN.matcher(path.getFileName().toString()).matches();
    }

    private static String genderOf(String payloadGender) {
        if (payloadGender == null) {
            return null;
        }
        return switch (payloadGender.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "masculino" -> "male";
            case "femenino" -> "female";
            default -> null;
        };
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String gender,
                                      String leagueCompetition,
                                      String group,
                                      List<FcttMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path reportFile : listMatchReportFiles(reportFolder)) {
            counters.filesSeen++;

            Acta acta;
            try {
                acta = actaParser.parse(reportFile);
            } catch (ActaParseException e) {
                counters.skipped++;
                LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                reportProgress(counters, progressListener);
                continue;
            }

            dispatchReport(reportFile, acta, season, gender, leagueCompetition, group, processors, counters,
                    runContext, progressListener);
        }
    }

    private void dispatchReport(Path reportFile, Acta acta, String season, String gender,
                                String leagueCompetition, String group,
                                List<FcttMatchReportProcessor> processors,
                                Counters counters, ImportRunContext runContext,
                                ImportProgressListener progressListener) {
        if (acta.round() == null) {
            counters.skipped++;
            LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
            reportProgress(counters, progressListener);
            return;
        }

        FcttMatchReportContext context = new FcttMatchReportContext(
                season, gender, leagueCompetition, group, acta.round(), reportFile, acta, runContext);
        if (context.hasGroupFolder() && context.groupNumber().isEmpty()) {
            counters.skipped++;
            LOGGER.warn("Skipping {}: group folder \"{}\" is not G<number> or <number>",
                    reportFile, group);
            reportProgress(counters, progressListener);
            return;
        }
        if (acta.gender() != null && !acta.gender().equals(context.sex())) {
            counters.skipped++;
            LOGGER.warn("Skipping {}: payload gender \"{}\" does not match the {} folder",
                    reportFile, acta.gender(), gender);
            reportProgress(counters, progressListener);
            return;
        }
        ActaClassification classification = classifier.classify(acta);
        if (classification.unresolvedPendingFixture()) {
            counters.skipped++;
            runContext.recordUnresolvedPendingFixture("FcttActasDirectoryNavigator", reportFile,
                    classification.reason());
            LOGGER.warn("Skipping {}: pending fixture has no teams; it is reported as unresolved "
                    + "and not dispatched", reportFile);
            reportProgress(counters, progressListener);
            return;
        }
        dispatch(context, processors, counters);
        reportProgress(counters, progressListener);
    }

    private static void reportProgress(Counters counters, ImportProgressListener progressListener) {
        progressListener.onProgress(ImportRunProgress.determinate(counters.filesSeen, counters.total,
                counters.skipped, counters.processorFailures));
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
            for (Path genderFolder : listDirectories(seasonFolder)) {
                if (!GENDER_FOLDERS.contains(genderFolder.getFileName().toString())) {
                    for (Path groupFolder : listDirectories(genderFolder)) {
                        for (Path phaseFolder : listDirectories(groupFolder)) {
                            total += list(phaseFolder, FcttActasDirectoryNavigator::isIngestReportFile).size();
                        }
                    }
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
        private long total;
        private long filesSeen;
        private long dispatched;
        private long skipped;
        private long processorFailures;
        private final List<ImportExecutionIssue> issues = new ArrayList<>();

        private TraversalSummary toSummary(ImportLifecycleCounters lifecycle) {
            return new TraversalSummary(filesSeen, dispatched, skipped, processorFailures, issues, lifecycle);
        }
    }
}
