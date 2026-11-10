package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportLifecycleCounters;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebActasDirectoryNavigator;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebContextFactory;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FcttWebTraversalCounters;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse.FixtureSplitter;
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
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * <h2>Ingest layout</h2>
 * <p>Files named {@code jornada_<NN>_local_team_<localId>_away_team_<awayId>.json} are the current
 * {@code tt-league-ingest} layout and are read by the shared {@link FcttWebActasDirectoryNavigator}
 * after the legacy pass of each season; this navigator keeps only the legacy layout, the multi-fixture
 * split and the unmapped-folder reporting. Both passes share one set of counters.</p>
 *
 * <h2>Report file names and competition folders</h2>
 * <p>Report names are recognised by {@link BcnesaReportFileNames}: the legacy {@code acta*.json}
 * names (16,310 {@code acta_<jornada>_page_<n>.json} and 77 {@code acta_<n>.json} files in 2020-2021
 * to 2025-2026, plus the earlier 2026-2027 {@code acta_<homeId>-<awayId>_<jornada>.json}) and the
 * current {@code jornada_<NN>_local_team_<localId>_away_team_<awayId>.json}. A {@code .json} file
 * under a phase folder with any other name is counted as skipped and reported as an issue, never
 * silently ignored. Names are otherwise not parsed: the match day comes from the payload's
 * {@code jornada} and the clubs from {@code equipos}. The one exception is a fixture under a BCNESA
 * Veterans "Other" group (see {@link BcnesaVeteransPhases}): if its payload carries no
 * {@code jornada}, the match day is parsed from the file name and reported through
 * {@link ImportRunContext#recordRoundFallback} so an operator sees it.</p>
 *
 * <p>Competition folders are mapped by {@link BcnesaCompetitionNames}: {@code rtb-*} folders of the
 * 2026-2027 export are stored under the legacy competition names, other folders unchanged. An
 * {@code rtb-*} folder with no mapping is not traversed; its files are counted as skipped and one
 * issue names the folder.</p>
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

    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");

    private final List<BcnesaMatchReportProcessor> processors;
    private final ActaParser actaParser;
    private final BcnesaMatchdaySplitter splitter;
    private final FcttWebActasDirectoryNavigator ingestNavigator;

    public BcnesaActasDirectoryNavigator(List<BcnesaMatchReportProcessor> processors, ActaParser actaParser) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.actaParser = actaParser;
        this.splitter = new BcnesaMatchdaySplitter();
        this.ingestNavigator = new FcttWebActasDirectoryNavigator(ImportSource.BCNESA, actaParser);
    }

    /**
     * Walks {@code baseFolder} and dispatches every readable fixture to {@code processors}. This is
     * the primary entry point: the processor list is explicit, and the injected list is ignored.
     */
    public BcnesaTraversalSummary traverse(Path baseFolder, List<BcnesaMatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season -> true, processors, new ImportRunContext(
                ImportSource.BCNESA, null),
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
                ImportSource.BCNESA, season),
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

        FcttWebTraversalCounters counters = new FcttWebTraversalCounters();
        counters.total(countReportFiles(baseFolder, seasonFilter));
        progressListener.onProgress(counters.progress());
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

        BcnesaTraversalSummary summary = toSummary(counters, runContext.lifecycleCounters());
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<BcnesaMatchReportProcessor> processors,
                                      FcttWebTraversalCounters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        for (Path competitionFolder : listDirectories(seasonFolder)) {
            String folderName = competitionFolder.getFileName().toString();
            Optional<String> storedName = BcnesaCompetitionNames.storedName(folderName);
            if (storedName.isEmpty()) {
                skipUnmappedCompetition(competitionFolder, folderName, counters);
                continue;
            }
            String leagueCompetition = storedName.get();
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
        ingestNavigator.traverseSeason(seasonFolder, season, runContext, counters, progressListener,
                BCNESA_CONTEXTS, context -> dispatch(context, processors, counters));
    }

    private static final FcttWebContextFactory<BcnesaMatchReportContext> BCNESA_CONTEXTS = report ->
            new BcnesaMatchReportContext(report.season(), report.competition(), report.group(), report.phase(),
                    report.round(), report.fixtureIndex(), report.homeTeamName(), report.awayTeamName(),
                    report.file(), report.acta(), report.games(), report.runContext());

    /**
     * A group folder is accepted when it fits the regular {@code G<n>} layout, or when it is the
     * literal "Other" group folder of a Veterans competition (see {@code BcnesaVeteransPhases}) -
     * the folder that holds every playoff/promotion/relegation/finals phase and whose fixtures carry
     * no numbered group. Any other competition's non-{@code G<n>} group folder is rejected (fail
     * closed) rather than silently imported under wrong assumptions.
     */
    private static boolean isAcceptedGroupFolder(String leagueCompetition, String group) {
        return FcttWebActasDirectoryNavigator.isAcceptedBcnesaGroupFolder(leagueCompetition, group);
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
                                      FcttWebTraversalCounters counters, ImportRunContext runContext,
                                      ImportProgressListener progressListener) throws IOException {
        FixtureSplitter fixtureSplitter = splitter.asFixtureSplitter(clubIndex);
        for (Path reportFile : listJsonFiles(reportFolder)) {
            String fileName = reportFile.getFileName().toString();
            if (ingestNavigator.isIngestReportName(fileName)) {
                // The ingest layout is read, and counted, by the shared navigator.
                continue;
            }
            counters.fileSeen();

            if (!BcnesaReportFileNames.isMatchReport(fileName)) {
                counters.fileSkipped();
                counters.issue(new ImportExecutionIssue("BcnesaActasDirectoryNavigator",
                        reportFile.toString(), "unrecognised match report file name"));
                LOGGER.warn("Skipping {}: unrecognised match report file name", reportFile);
                progressListener.onProgress(counters.progress());
                continue;
            }

            ingestNavigator.handleBcnesaReport(reportFile, season, leagueCompetition, group, phase,
                    fixtureSplitter, BCNESA_CONTEXTS, context -> dispatch(context, processors, counters),
                    runContext, counters, progressListener);
        }
    }

    /**
     * Counts the files of an unmapped {@code rtb-*} competition folder as seen and skipped, using
     * the same group and phase walk as a mapped one, and reports one issue for the folder.
     */
    private void skipUnmappedCompetition(Path competitionFolder, String folderName,
                                         FcttWebTraversalCounters counters)
            throws IOException {
        long files = countFolderFiles(competitionFolder, folderName);
        counters.filesSeenAndSkipped(files);
        counters.issue(new ImportExecutionIssue("BcnesaActasDirectoryNavigator", competitionFolder.toString(),
                "unmapped BCNESA competition folder " + folderName + "; add it to BcnesaCompetitionNames"));
        LOGGER.error("Skipping unmapped BCNESA competition folder {}", competitionFolder);
    }

    private long countFolderFiles(Path competitionFolder, String leagueCompetition) throws IOException {
        long total = 0;
        for (Path groupFolder : listDirectories(competitionFolder)) {
            if (!isAcceptedGroupFolder(leagueCompetition, groupFolder.getFileName().toString())) {
                continue;
            }
            for (Path phaseFolder : listDirectories(groupFolder)) {
                total += listJsonFiles(phaseFolder).size();
            }
        }
        return total;
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
                String folderName = competitionFolder.getFileName().toString();
                total += countFolderFiles(competitionFolder,
                        BcnesaCompetitionNames.storedName(folderName).orElse(folderName));
            }
        }
        return total;
    }

    private void dispatch(BcnesaMatchReportContext context,
                          List<BcnesaMatchReportProcessor> processors,
                          FcttWebTraversalCounters counters) {
        counters.fixtureDispatched();
        for (BcnesaMatchReportProcessor processor : processors) {
            try {
                processor.process(context);
            } catch (RuntimeException e) {
                counters.processorFailed();
                counters.issue(new ImportExecutionIssue(processor.getClass().getSimpleName(),
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
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
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

    private static BcnesaTraversalSummary toSummary(FcttWebTraversalCounters counters,
                                                    ImportLifecycleCounters lifecycle) {
        return new BcnesaTraversalSummary(counters.filesSeen(), counters.filesSkipped(), counters.fixturesSeen(),
                counters.fixturesDispatched(), counters.fixturesUnresolved(), counters.processorFailures(),
                counters.issues(), lifecycle);
    }
}
