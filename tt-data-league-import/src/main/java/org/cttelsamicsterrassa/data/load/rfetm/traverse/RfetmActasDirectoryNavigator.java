package org.cttelsamicsterrassa.data.load.rfetm.traverse;

import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeams;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportProcessor;
import org.cttelsamicsterrassa.data.load.shared.traverse.TraversalSummary;
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
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Walks an RFETM {@code actas-json} export and hands one {@link MatchReportContext} per match report
 * to a list of {@link MatchReportProcessor}s.
 *
 * <p>The expected layout is</p>
 *
 * <pre>[baseFolder]/[season]/[league-competition]/[day]/[sex]/acta.json</pre>
 *
 * <p>This class knows only about that shape and about the processor contract; which processors run
 * is a parameter of {@link #traverse(Path, List)}, so a caller can run a reporting pass and a
 * persisting pass over the same tree without changing anything here.</p>
 *
 * <h2>Report file names are opaque</h2>
 * <p>A report file is any {@code acta*.json} under a gender folder - {@code acta.json} or
 * {@code acta_<matchId>.json}, where the suffix is an arbitrary identifier carrying no meaning for the
 * import. The name is never parsed: the teams of a match come from the payload alone.</p>
 *
 * <h2>Teams come from the payload, by id or by name</h2>
 * <p>{@code equipos.*.id} carries the federation team id in 16,106 of the 20,619 reports and is
 * absent from the other 4,513 - among them every one of the 4,017 files of {@code 2025-2026}. A
 * report without ids still names its teams, so identity falls back to {@code equipos.*.nombre} scoped
 * to the season and competition rather than being thrown away; {@link RfetmClubKey} holds that rule
 * and the measurements behind it.</p>
 *
 * <h2>Failure handling</h2>
 * <p>Nothing a single file can do aborts the run. Folders that do not fit the layout are logged and
 * skipped; so are payloads that fail to parse. A report that identifies a side by neither id nor name
 * cannot be attributed to a club and is skipped rather than guessed at. Each processor call is
 * isolated, so a processor that throws neither stops the traversal nor prevents its peers from seeing
 * the same file. Every category is counted and reported in the returned {@link TraversalSummary}.</p>
 */
@Component
public class RfetmActasDirectoryNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmActasDirectoryNavigator.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("acta.*\\.json");
    private static final Pattern SEASON_FOLDER_PATTERN = Pattern.compile("\\d{4}-\\d{4}");
    private static final Pattern DAY_FOLDER_PATTERN = Pattern.compile("\\d+");
    private static final Set<String> SEX_FOLDERS = Set.of("masculino", "femenino");

    private final List<MatchReportProcessor> processors;
    private final ActaParser actaParser;

    public RfetmActasDirectoryNavigator(List<MatchReportProcessor> processors, ActaParser actaParser) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.actaParser = actaParser;
    }

    /**
     * Walks {@code baseFolder} and dispatches every readable match report to {@code processors}.
     * This is the primary entry point: the processor list is explicit, and the injected list is
     * ignored.
     */
    public TraversalSummary traverse(Path baseFolder, List<MatchReportProcessor> processors) throws IOException {
        return traverse(baseFolder, season -> true, processors);
    }

    /**
     * Walks {@code baseFolder} and dispatches to the injected processors.
     */
    public TraversalSummary traverse(Path baseFolder) throws IOException {
        return traverse(baseFolder, this.processors);
    }

    /**
     * Walks {@code baseFolder} and dispatches to the injected processors.
     */
    public TraversalSummary traverse(String baseFolder) throws IOException {
        return traverse(Paths.get(baseFolder));
    }

    /**
     * Walks a single season of {@code baseFolder}.
     *
     * @param season season folder name, in {@code YYYY-YYYY} form
     */
    public TraversalSummary traverseSeason(Path baseFolder, String season, List<MatchReportProcessor> processors)
            throws IOException {
        return traverse(baseFolder, season::equals, processors);
    }

    /**
     * Walks a single season of {@code baseFolder}, dispatching to the injected processors.
     */
    public TraversalSummary traverseSeason(Path baseFolder, String season) throws IOException {
        return traverseSeason(baseFolder, season, this.processors);
    }

    private TraversalSummary traverse(Path baseFolder,
                                      Predicate<String> seasonFilter,
                                      List<MatchReportProcessor> processors) throws IOException {
        if (!Files.isDirectory(baseFolder)) {
            throw new IOException("Base folder is not a directory: " + baseFolder);
        }
        if (processors.isEmpty()) {
            LOGGER.warn("Traversing {} with no processors; nothing will be stored", baseFolder);
        }

        Counters counters = new Counters();
        LOGGER.info("Traversing RFETM match reports under {}", baseFolder);

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

        TraversalSummary summary = counters.toSummary();
        LOGGER.info("Traversal of {} finished: {}", baseFolder, summary);
        return summary;
    }

    private void traverseSeasonFolder(Path seasonFolder,
                                      String season,
                                      List<MatchReportProcessor> processors,
                                      Counters counters) throws IOException {
        for (Path competitionFolder : listDirectories(seasonFolder)) {
            String leagueCompetition = competitionFolder.getFileName().toString();
            for (Path dayFolder : listDirectories(competitionFolder)) {
                String day = dayFolder.getFileName().toString();
                if (!DAY_FOLDER_PATTERN.matcher(day).matches()) {
                    LOGGER.warn("Skipping unexpected match day folder {}", dayFolder);
                    continue;
                }
                for (Path sexFolder : listDirectories(dayFolder)) {
                    String sex = sexFolder.getFileName().toString();
                    if (!SEX_FOLDERS.contains(sex)) {
                        LOGGER.warn("Skipping unexpected gender folder {}", sexFolder);
                        continue;
                    }
                    traverseReportFolder(sexFolder, season, leagueCompetition, day, sex, processors, counters);
                }
            }
        }
    }

    private void traverseReportFolder(Path reportFolder,
                                      String season,
                                      String leagueCompetition,
                                      String day,
                                      String sex,
                                      List<MatchReportProcessor> processors,
                                      Counters counters) throws IOException {
        for (Path reportFile : listJsonFiles(reportFolder)) {
            counters.filesSeen++;

            Acta acta;
            try {
                acta = actaParser.parse(reportFile);
            } catch (ActaParseException e) {
                counters.skipped++;
                LOGGER.error("Skipping {}: {}", reportFile, e.getMessage());
                continue;
            }

            String competition = MatchReportContext.competitionOf(leagueCompetition, sex);
            RfetmClubKey homeClub = clubKey(acta, true, season, competition);
            RfetmClubKey awayClub = clubKey(acta, false, season, competition);
            if (homeClub == null || awayClub == null) {
                counters.skipped++;
                LOGGER.warn("Skipping {}: payload identifies its teams by neither id nor name", reportFile);
                continue;
            }

            MatchReportContext context = new MatchReportContext(
                    season, leagueCompetition, day, sex, homeClub, awayClub, reportFile, acta);
            dispatch(context, processors, counters);
        }
    }

    /**
     * How one side is matched to a club: its federation id when the payload carries one, its name
     * scoped to this season and competition otherwise. {@code null} when the payload offers neither
     * and the side cannot be identified at all.
     */
    private static RfetmClubKey clubKey(Acta acta, boolean home, String season, String competition) {
        ActaTeams teams = acta.teams();
        if (teams == null) {
            return null;
        }
        ActaTeam team = home ? teams.home() : teams.away();
        if (team == null) {
            return null;
        }
        return RfetmClubKey.of(team.rfetmId(), team.name(), season, competition);
    }

    private void dispatch(MatchReportContext context, List<MatchReportProcessor> processors, Counters counters) {
        counters.dispatched++;
        for (MatchReportProcessor processor : processors) {
            try {
                processor.process(context);
            } catch (RuntimeException e) {
                counters.processorFailures++;
                LOGGER.error("Processor {} failed on {}",
                        processor.getClass().getSimpleName(), context.matchReportFile(), e);
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
        private long dispatched;
        private long skipped;
        private long processorFailures;

        private TraversalSummary toSummary() {
            return new TraversalSummary(filesSeen, dispatched, skipped, processorFailures);
        }
    }
}
