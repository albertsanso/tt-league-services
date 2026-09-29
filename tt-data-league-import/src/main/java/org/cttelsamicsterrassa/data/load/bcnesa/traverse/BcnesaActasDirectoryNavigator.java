package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.load.bcnesa.BcnesaVeteransPhases;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks a BCNESA {@code actas-json} export and hands one {@link BcnesaMatchReportContext} per fixture
 * to a list of {@link BcnesaMatchReportProcessor}s.
 *
 * <p>The expected layout is</p>
 *
 * <pre>[baseFolder]/[season]/[league-competition]/[group]/[phase]/acta.json</pre>
 *
 * <p>Like the RFETM export, a BCNESA file holds one match: measured over the whole export (16,387
 * files, 2020-2021 to 2025-2026), every file holds exactly one fixture, named by its {@code equipos}.
 * Each file is still split into fixtures via {@link BcnesaMatchdaySplitter}, which also accepts a
 * file holding a whole matchday back to back; the clubs of any fixture after the first are then
 * inferred from licences. Because no file in the export splits today, {@link BcnesaClubIndex} is no
 * longer read eagerly for every group folder: it is built lazily, once per group, only for the first
 * file that actually yields more than one fixture. Which processors run is a parameter of
 * {@link #traverse(Path, List)}, so a caller can run a reporting pass and a persisting pass over the
 * same tree without changing anything here.</p>
 *
 * <h2>Report file names are not parsed</h2>
 * <p>A report file is any {@code acta*.json} under a phase folder. Two names exist in the export:
 * 16,310 legacy files (2020-2021 to 2025-2026) are named {@code acta_<jornada>_page_<n>.json} - one
 * page per fixture of that match day - and 77 are named {@code acta_<n>.json}, while all 2,882 files
 * of the unpublished 2026-2027 season are named {@code acta_<homeId>-<awayId>_<jornada>.json}. The
 * name is never parsed: the match day comes from the payload's {@code jornada}, present in all 16,387
 * legacy files (and equal to the name's {@code <jornada>} in every {@code _page_} file), and the
 * clubs from {@code equipos}. The one exception is a fixture under a BCNESA Veterans "Other" group
 * (see {@link BcnesaVeteransPhases}): if its payload carries no {@code jornada}, the match day is
 * instead parsed from either file-name pattern and reported through
 * {@link ImportRunContext#recordRoundFallback} so an operator sees it.</p>
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

    /**
     * Fallback sources for the match day when a payload under a Veterans "Other" group carries no
     * {@code jornada} (see {@link BcnesaVeteransPhases}). Two report names exist in the export:
     * <ul>
     *   <li>legacy {@code acta_<jornada>_page_<n>.json} (up to 2025-2026), where {@code <jornada>}
     *       mirrors the source PDF's {@code acta_<number>_page_<*>.pdf} naming;</li>
     *   <li>current {@code acta_<homeId>-<awayId>_<jornada>.json} (2026-2027 onward), whose trailing
     *       segment is the jornada.</li>
     * </ul>
     * Both are tried, legacy first. The fallback has not yet been needed: all 2,586 files under
     * "Other" group folders in the export carry {@code jornada}.
     */
    private static final Pattern OTHER_GROUP_LEGACY_ROUND_FROM_FILE_NAME =
            Pattern.compile("acta_(\\d+)_page_.*\\.json", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_GROUP_ROUND_FROM_FILE_NAME =
            Pattern.compile("acta_\\d+-\\d+_(\\d+)\\.json", Pattern.CASE_INSENSITIVE);

    private final List<BcnesaMatchReportProcessor> processors;
    private final ActaParser actaParser;
    private final BcnesaMatchdaySplitter splitter;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();

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
        return traverse(baseFolder, season -> true, processors, new ImportRunContext(
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.BCNESA, null),
                ImportProgressListener.noop());
    }
    public BcnesaTraversalSummary traverse(Path baseFolder, List<BcnesaMatchReportProcessor> processors,
                                           ImportRunContext context) throws IOException {
        return traverse(baseFolder, season -> true, processors, context, ImportProgressListener.noop());
    }

    /**
     * As {@link #traverse(Path, List, ImportRunContext)}, additionally reporting file-level progress
     * while the traversal runs.
     */
    public BcnesaTraversalSummary traverse(Path baseFolder, List<BcnesaMatchReportProcessor> processors,
                                           ImportRunContext context, ImportProgressListener progressListener)
            throws IOException {
        return traverse(baseFolder, season -> true, processors, context, progressListener);
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
        return traverse(baseFolder, season::equals, processors, new ImportRunContext(
                org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.BCNESA, season),
                ImportProgressListener.noop());
    }
    public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season,
                                                  List<BcnesaMatchReportProcessor> processors,
                                                  ImportRunContext context) throws IOException {
        return traverse(baseFolder, season::equals, processors, context, ImportProgressListener.noop());
    }

    /**
     * As {@link #traverseSeason(Path, String, List, ImportRunContext)}, additionally reporting
     * file-level progress while the traversal runs.
     */
    public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season,
                                                  List<BcnesaMatchReportProcessor> processors,
                                                  ImportRunContext context, ImportProgressListener progressListener)
            throws IOException {
        return traverse(baseFolder, season::equals, processors, context, progressListener);
    }

    /**
     * Walks a single season of {@code baseFolder}, dispatching to the injected processors.
     */
    public BcnesaTraversalSummary traverseSeason(Path baseFolder, String season) throws IOException {
        return traverseSeason(baseFolder, season, this.processors);
    }

    private BcnesaTraversalSummary traverse(Path baseFolder,
                                            Predicate<String> seasonFilter,
                                            List<BcnesaMatchReportProcessor> processors,
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
            traverseSeasonFolder(seasonFolder, season, processors, counters, runContext, progressListener);
        }

        BcnesaTraversalSummary summary = counters.toSummary(runContext.lifecycleCounters());
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<BcnesaMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path competitionFolder : listDirectories(seasonFolder)) {
            String leagueCompetition = competitionFolder.getFileName().toString();
            for (Path groupFolder : listDirectories(competitionFolder)) {
                String group = groupFolder.getFileName().toString();
                if (!isAcceptedGroupFolder(leagueCompetition, group)) {
                    LOGGER.warn("Skipping unexpected group folder {}", groupFolder);
                    continue;
                }
                Supplier<BcnesaClubIndex> clubIndex = lazyClubIndex(groupFolder);
                for (Path phaseFolder : listDirectories(groupFolder)) {
                    String phase = phaseFolder.getFileName().toString();
                    traverseReportFolder(phaseFolder, season, leagueCompetition, group, phase, clubIndex,
                            processors, counters, runContext, progressListener);
                }
            }
        }
    }

    /**
     * A group folder is accepted when it fits the regular {@code G<n>} layout, or when it is the
     * literal "Other" group folder of a Veterans competition (see {@link BcnesaVeteransPhases}) -
     * the folder that holds every playoff/promotion/relegation/finals phase and whose fixtures carry
     * no numbered group. Any other competition's non-{@code G<n>} group folder is rejected (fail
     * closed) rather than silently imported under wrong assumptions.
     */
    private static boolean isAcceptedGroupFolder(String leagueCompetition, String group) {
        return GROUP_FOLDER_PATTERN.matcher(group).matches()
                || BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group);
    }

    /**
     * A memoizing supplier for one group's {@link BcnesaClubIndex}, built the first time a fixture
     * actually needs it. The index is consulted only for the second and later fixtures of a
     * multi-fixture file; no group in the export has one today, so the per-group pre-read is avoided
     * entirely. {@link BcnesaClubIndex#build} declares {@link IOException}; it is wrapped here and
     * unwrapped at the call site in {@link #traverseReportFolder} so this class keeps its
     * {@code throws IOException} contract.
     */
    private Supplier<BcnesaClubIndex> lazyClubIndex(Path groupFolder) {
        return new Supplier<>() {
            private BcnesaClubIndex index;

            @Override
            public BcnesaClubIndex get() {
                if (index == null) {
                    try {
                        index = BcnesaClubIndex.build(groupFolder, actaParser);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return index;
            }
        };
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String leagueCompetition,
                                      String group,
                                      String phase,
                                      Supplier<BcnesaClubIndex> clubIndex,
                                      List<BcnesaMatchReportProcessor> processors,
                                      Counters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path reportFile : listJsonFiles(reportFolder)) {
            counters.filesSeen++;

            Acta acta;
            try {
                acta = actaParser.parse(reportFile);
            } catch (ActaParseException e) {
                counters.filesSkipped++;
                LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                reportProgress(counters, progressListener);
                continue;
            }

            Integer round = acta.round();
            if (round == null && BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group)) {
                round = parseRoundFromFileName(reportFile);
                if (round != null) {
                    runContext.recordRoundFallback("BcnesaActasDirectoryNavigator", reportFile,
                            "payload carries no jornada; round " + round + " taken from the file name");
                }
            }
            if (round == null) {
                counters.filesSkipped++;
                LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
                reportProgress(counters, progressListener);
                continue;
            }

            List<BcnesaMatchdaySplitter.Fixture> fixtures;
            try {
                fixtures = splitter.split(acta, clubIndex);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            for (int i = 0; i < fixtures.size(); i++) {
                counters.fixturesSeen++;
                dispatchFixture(reportFile, season, leagueCompetition, group, phase, round, i, fixtures.get(i),
                        acta, processors, counters, runContext);
            }
            reportProgress(counters, progressListener);
        }
    }

    /**
     * Parses the match day out of a Veterans "Other"-group file name, trying the legacy
     * {@code acta_<jornada>_page_<n>.json} pattern and then the current
     * {@code acta_<homeId>-<awayId>_<jornada>.json} pattern. Returns {@code null} when the name fits
     * neither, so the caller skips the file exactly as it does when the payload itself carries no
     * {@code jornada}.
     */
    private static Integer parseRoundFromFileName(Path reportFile) {
        String fileName = reportFile.getFileName().toString();
        Matcher legacy = OTHER_GROUP_LEGACY_ROUND_FROM_FILE_NAME.matcher(fileName);
        if (legacy.matches()) {
            return Integer.valueOf(legacy.group(1));
        }
        Matcher current = OTHER_GROUP_ROUND_FROM_FILE_NAME.matcher(fileName);
        return current.matches() ? Integer.valueOf(current.group(1)) : null;
    }

    /**
     * Reports progress in file-level units to match {@code total} (a file count, not a fixture
     * count, since fixture counts per file are only known after parsing). {@code fixturesDispatched}
     * remains the unit of the final {@link BcnesaTraversalSummary}.
     */
    private static void reportProgress(Counters counters, ImportProgressListener progressListener) {
        progressListener.onProgress(ImportRunProgress.determinate(counters.filesSeen, counters.total,
                counters.filesSkipped + counters.fixturesUnresolved, counters.processorFailures));
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
            for (Path competitionFolder : listDirectories(seasonFolder)) {
                String leagueCompetition = competitionFolder.getFileName().toString();
                for (Path groupFolder : listDirectories(competitionFolder)) {
                    String group = groupFolder.getFileName().toString();
                    if (!isAcceptedGroupFolder(leagueCompetition, group)) {
                        continue;
                    }
                    for (Path phaseFolder : listDirectories(groupFolder)) {
                        total += listJsonFiles(phaseFolder).size();
                    }
                }
            }
        }
        return total;
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
                                 Counters counters, ImportRunContext runContext) {
        if (!fixture.isResolved()) {
            counters.fixturesUnresolved++;
            ActaClassification classification = classifier.classify(acta, fixture.games());
            if (classification.unresolvedPendingFixture()) {
                runContext.recordUnresolvedPendingFixture("BcnesaActasDirectoryNavigator", reportFile,
                        classification.reason());
            }
            LOGGER.warn("Skipping fixture {} of {}: clubs could not be attributed ({} games)",
                    fixtureIndex, reportFile, fixture.games().size());
            return;
        }

        BcnesaMatchReportContext context = new BcnesaMatchReportContext(
                season, leagueCompetition, group, phase, round, fixtureIndex,
                fixture.homeTeamName(), fixture.awayTeamName(), reportFile,                 acta, fixture.games(), runContext);
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
                counters.issues.add(new ImportExecutionIssue(processor.getClass().getSimpleName(),
                        context.matchReportFile().toString(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
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
        private long total;
        private long filesSeen;
        private long filesSkipped;
        private long fixturesSeen;
        private long fixturesDispatched;
        private long fixturesUnresolved;
        private long processorFailures;
        private final List<ImportExecutionIssue> issues = new ArrayList<>();

        private BcnesaTraversalSummary toSummary(ImportLifecycleCounters lifecycle) {
            return new BcnesaTraversalSummary(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched,
                    fixturesUnresolved, processorFailures, issues, lifecycle);
        }
    }
}
