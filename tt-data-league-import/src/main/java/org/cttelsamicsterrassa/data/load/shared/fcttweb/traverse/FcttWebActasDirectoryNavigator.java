package org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.service.ImportProgressListener;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.bcnesa.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.load.bcnesa.BcnesaVeteransPhases;
import org.cttelsamicsterrassa.data.load.bcnesa.traverse.BcnesaReportFileNames;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebFixtureContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Walks the {@code tt-league-ingest} layout of a fctt.cat export for one {@link ImportSource} and
 * hands one context per fixture to a sink:
 *
 * <pre>[season]/[category]/[group]/[phase]/jornada_[N|NN]_local_team_[id]_away_team_[id].json</pre>
 *
 * <p>It is the one reader of that layout. {@code BcnesaActasDirectoryNavigator} and
 * {@code FcttActasDirectoryNavigator} delegate their ingest-layout files to it and keep only their
 * legacy layouts; they also reuse {@link #handleBcnesaReport} and {@link #handleFcttReport} for the
 * files of those layouts, so the acceptance rules of a parsed report live in one place. Counting and
 * progress go through a caller-owned {@link FcttWebTraversalCounters}, so a run that mixes layouts
 * reports one summary.</p>
 *
 * <h2>Per source</h2>
 * <ul>
 *   <li>{@link ImportSource#BCNESA}: the category folder is mapped by {@link BcnesaCompetitionNames}
 *       ({@code rtb-*} to the legacy competition names; an unmapped folder is not read here, the BCNESA
 *       navigator reports it); the group folder is {@code G<n>} or the Veterans "Other" group; the
 *       match day comes from the payload, from the file name only for a Veterans "Other" file without
 *       one; a fixture whose clubs cannot be attributed is counted and reported, never guessed.</li>
 *   <li>{@link ImportSource#FCTT}: the category folder is the competition, with {@code tdm} stored as
 *       {@code tercera-nacional}; the legacy {@code male}/{@code female} folders are not read here; the
 *       gender comes from the payload's {@code genero}; a group folder that is not {@code g<n>}
 *       dispatches with a {@code null} group; the phase folder is informational.</li>
 * </ul>
 *
 * <p>Nothing a single file can do aborts the run: unreadable payloads and reports without a match day
 * are logged, counted as skipped and walked past.</p>
 */
public final class FcttWebActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttWebActasDirectoryNavigator.class);

    private static final String BCNESA_REPORTER = "BcnesaActasDirectoryNavigator";
    private static final String FCTT_REPORTER = "FcttActasDirectoryNavigator";

    private static final Pattern FCTT_INGEST_FILE =
            Pattern.compile("jornada_\\d+_local_team_[^_]+_away_team_[^_]+\\.json");
    private static final Pattern FCTT_INGEST_GROUP = Pattern.compile("(?i)g\\d+");
    private static final Pattern BCNESA_GROUP = Pattern.compile("G\\d+");

    /**
     * Ingest category folders whose stored competition keeps the name of the legacy layout, so both
     * layouts feed the same competition. A category without an entry keeps its own folder name.
     */
    private static final Map<String, String> FCTT_CATEGORY_ALIASES = Map.of("tdm", "tercera-nacional");

    /** The gender folders of the legacy FCTT layout: not part of the ingest layout. */
    private static final Set<String> FCTT_LEGACY_GENDER_FOLDERS = Set.of("male", "female");

    private final ImportSource source;
    private final ActaParser actaParser;
    private final FixtureSplitter splitter;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();

    public FcttWebActasDirectoryNavigator(ImportSource source, ActaParser actaParser) {
        this(source, actaParser, FixtureSplitter.wholeFile());
    }

    /**
     * @param splitter how a report of the ingest layout is split into fixtures; {@link
     *                 FixtureSplitter#wholeFile()} unless a source ever delivers several fixtures per file
     */
    public FcttWebActasDirectoryNavigator(ImportSource source, ActaParser actaParser, FixtureSplitter splitter) {
        if (source != ImportSource.BCNESA && source != ImportSource.FCTT) {
            throw new IllegalArgumentException("fctt.cat exports exist for BCNESA and FCTT only: " + source);
        }
        this.source = source;
        this.actaParser = actaParser;
        this.splitter = Objects.requireNonNull(splitter, "splitter");
    }

    public ImportSource source() {
        return source;
    }

    // --- layout rules ----------------------------------------------------------------------

    /** Whether {@code fileName} follows the ingest naming of this source. */
    public boolean isIngestReportName(String fileName) {
        return source == ImportSource.BCNESA
                ? BcnesaReportFileNames.isIngestReport(fileName)
                : FCTT_INGEST_FILE.matcher(fileName).matches();
    }

    /**
     * Whether a BCNESA group folder is read: the regular {@code G<n>} layout, or the literal "Other"
     * group of a Veterans competition. Any other folder is rejected (fail closed) rather than imported
     * under wrong assumptions.
     */
    public static boolean isAcceptedBcnesaGroupFolder(String leagueCompetition, String group) {
        return BCNESA_GROUP.matcher(group).matches() || BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group);
    }

    /** The ingest category folders of a FCTT season: every folder but the legacy gender ones. */
    public static boolean isFcttLegacyGenderFolder(String folderName) {
        return FCTT_LEGACY_GENDER_FOLDERS.contains(folderName);
    }

    // --- walk ------------------------------------------------------------------------------

    /**
     * The number of ingest-layout reports under {@code seasonFolder}, using the same folder and file
     * predicates as {@link #traverseSeason} without parsing anything.
     */
    public long countReportFiles(Path seasonFolder) throws IOException {
        long total = 0;
        for (Category category : readableCategories(seasonFolder)) {
            for (Path groupFolder : listDirectories(category.folder())) {
                if (!isReadableGroup(category.competition(), groupFolder)) {
                    continue;
                }
                for (Path phaseFolder : listDirectories(groupFolder)) {
                    total += list(phaseFolder, this::isIngestReportFile).size();
                }
            }
        }
        return total;
    }

    /**
     * Walks one season folder and hands every readable ingest-layout fixture to {@code sink}.
     * Files are counted in {@code counters}; dispatch counting belongs to the sink's owner.
     */
    public <C extends FcttWebFixtureContext> void traverseSeason(Path seasonFolder,
                                                                 String season,
                                                                 ImportRunContext runContext,
                                                                 FcttWebTraversalCounters counters,
                                                                 ImportProgressListener progressListener,
                                                                 FcttWebContextFactory<C> factory,
                                                                 Consumer<C> sink) throws IOException {
        for (Category category : readableCategories(seasonFolder)) {
            for (Path groupFolder : listDirectories(category.folder())) {
                if (!isReadableGroup(category.competition(), groupFolder)) {
                    LOGGER.debug("Skipping group folder {} of the ingest layout", groupFolder);
                    continue;
                }
                String groupName = groupFolder.getFileName().toString();
                String group = source == ImportSource.BCNESA ? groupName
                        : FCTT_INGEST_GROUP.matcher(groupName).matches() ? groupName : null;
                for (Path phaseFolder : listDirectories(groupFolder)) {
                    String phase = phaseFolder.getFileName().toString();
                    for (Path reportFile : list(phaseFolder, this::isIngestReportFile)) {
                        counters.fileSeen();
                        if (source == ImportSource.BCNESA) {
                            handleBcnesaReport(reportFile, season, category.competition(), group, phase, splitter,
                                    factory, sink, runContext, counters, progressListener);
                        } else {
                            handleFcttReport(reportFile, season, null, category.competition(), group, factory,
                                    sink, runContext, counters, progressListener);
                        }
                    }
                }
            }
        }
    }

    // --- one report ------------------------------------------------------------------------

    /**
     * Reads one BCNESA report, splits it into fixtures with {@code fixtureSplitter} and hands every
     * attributed fixture to {@code sink}. The caller has already counted the file as seen.
     */
    public <C extends FcttWebFixtureContext> void handleBcnesaReport(Path reportFile,
                                                                     String season,
                                                                     String leagueCompetition,
                                                                     String group,
                                                                     String phase,
                                                                     FixtureSplitter fixtureSplitter,
                                                                     FcttWebContextFactory<C> factory,
                                                                     Consumer<C> sink,
                                                                     ImportRunContext runContext,
                                                                     FcttWebTraversalCounters counters,
                                                                     ImportProgressListener progressListener)
            throws IOException {
        Acta acta;
        try {
            acta = actaParser.parse(reportFile);
        } catch (ActaParseException e) {
            counters.fileSkipped();
            LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
            progressListener.onProgress(counters.progress());
            return;
        }

        Integer round = acta.round();
        if (round == null && BcnesaVeteransPhases.isOtherGroup(leagueCompetition, group)) {
            round = BcnesaReportFileNames.roundFromFileName(reportFile.getFileName().toString());
            if (round != null) {
                runContext.recordRoundFallback(BCNESA_REPORTER, reportFile,
                        "payload carries no jornada; round " + round + " taken from the file name");
            }
        }
        if (round == null) {
            counters.fileSkipped();
            LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
            progressListener.onProgress(counters.progress());
            return;
        }

        List<FixtureSplitter.Fixture> fixtures;
        try {
            fixtures = fixtureSplitter.split(acta);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        for (int i = 0; i < fixtures.size(); i++) {
            counters.fixtureSeen();
            FixtureSplitter.Fixture fixture = fixtures.get(i);
            if (!fixture.isResolved()) {
                counters.fixtureUnresolved();
                ActaClassification classification = classifier.classify(acta, fixture.games());
                if (classification.unresolvedPendingFixture()) {
                    runContext.recordUnresolvedPendingFixture(BCNESA_REPORTER, reportFile, classification.reason());
                }
                LOGGER.warn("Skipping fixture {} of {}: clubs could not be attributed ({} games)",
                        i, reportFile, fixture.games().size());
                continue;
            }
            sink.accept(factory.create(new FcttWebContextFactory.Report(season, leagueCompetition, null, group,
                    phase, round, i, fixture.homeTeamName(), fixture.awayTeamName(), reportFile, acta,
                    fixture.games(), runContext)));
        }
        progressListener.onProgress(counters.progress());
    }

    /**
     * Reads one FCTT report and, once it passes the acceptance rules, hands it to {@code sink}. The
     * caller has already counted the file as seen.
     *
     * @param genderFolder {@code male} or {@code female} for the legacy layout, {@code null} for the
     *                     ingest layout, where the gender comes from the payload's {@code genero}
     */
    public <C extends FcttWebFixtureContext> void handleFcttReport(Path reportFile,
                                                                   String season,
                                                                   String genderFolder,
                                                                   String leagueCompetition,
                                                                   String group,
                                                                   FcttWebContextFactory<C> factory,
                                                                   Consumer<C> sink,
                                                                   ImportRunContext runContext,
                                                                   FcttWebTraversalCounters counters,
                                                                   ImportProgressListener progressListener) {
        Acta acta;
        try {
            acta = actaParser.parse(reportFile);
        } catch (ActaParseException e) {
            counters.fileSkipped();
            LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
            progressListener.onProgress(counters.progress());
            return;
        }
        String gender = genderFolder != null ? genderFolder : genderOf(acta.gender());
        if (gender == null) {
            counters.processorFailed();
            counters.issue(new ImportExecutionIssue(FCTT_REPORTER, reportFile.toString(),
                    "payload genero \"" + acta.gender() + "\" is not masculino or femenino"));
            progressListener.onProgress(counters.progress());
            return;
        }

        if (acta.round() == null) {
            counters.fileSkipped();
            LOGGER.warn("Skipping {}: payload carries no match day", reportFile);
            progressListener.onProgress(counters.progress());
            return;
        }
        C context = factory.create(new FcttWebContextFactory.Report(season, leagueCompetition, gender, group, null,
                acta.round(), 0, null, null, reportFile, acta, acta.games(), runContext));
        if (context.hasInvalidGroupFolder()) {
            counters.fileSkipped();
            LOGGER.warn("Skipping {}: group folder \"{}\" is not G<number> or <number>", reportFile, group);
            progressListener.onProgress(counters.progress());
            return;
        }
        if (acta.gender() != null && !acta.gender().equals(context.sex())) {
            counters.fileSkipped();
            LOGGER.warn("Skipping {}: payload gender \"{}\" does not match the {} folder",
                    reportFile, acta.gender(), gender);
            progressListener.onProgress(counters.progress());
            return;
        }
        ActaClassification classification = classifier.classify(acta);
        if (classification.unresolvedPendingFixture()) {
            counters.fileSkipped();
            runContext.recordUnresolvedPendingFixture(FCTT_REPORTER, reportFile, classification.reason());
            LOGGER.warn("Skipping {}: pending fixture has no teams; it is reported as unresolved "
                    + "and not dispatched", reportFile);
            progressListener.onProgress(counters.progress());
            return;
        }
        sink.accept(context);
        progressListener.onProgress(counters.progress());
    }

    private static String genderOf(String payloadGender) {
        if (payloadGender == null) {
            return null;
        }
        return switch (payloadGender.trim().toLowerCase(Locale.ROOT)) {
            case "masculino" -> "male";
            case "femenino" -> "female";
            default -> null;
        };
    }

    // --- folders ---------------------------------------------------------------------------

    private record Category(Path folder, String competition) {
    }

    /** The category folders of {@code seasonFolder} this navigator reads, with their stored competition. */
    private List<Category> readableCategories(Path seasonFolder) throws IOException {
        List<Category> categories = new ArrayList<>();
        for (Path folder : listDirectories(seasonFolder)) {
            String name = folder.getFileName().toString();
            if (source == ImportSource.BCNESA) {
                Optional<String> storedName = BcnesaCompetitionNames.storedName(name);
                storedName.ifPresent(competition -> categories.add(new Category(folder, competition)));
            } else if (!isFcttLegacyGenderFolder(name)) {
                categories.add(new Category(folder, FCTT_CATEGORY_ALIASES.getOrDefault(name, name)));
            }
        }
        return categories;
    }

    private boolean isReadableGroup(String competition, Path groupFolder) {
        return source != ImportSource.BCNESA
                || isAcceptedBcnesaGroupFolder(competition, groupFolder.getFileName().toString());
    }

    private boolean isIngestReportFile(Path path) {
        return Files.isRegularFile(path) && isIngestReportName(path.getFileName().toString());
    }

    private static List<Path> listDirectories(Path folder) throws IOException {
        return list(folder, Files::isDirectory);
    }

    private static List<Path> list(Path folder, Predicate<Path> accepted) throws IOException {
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
}
