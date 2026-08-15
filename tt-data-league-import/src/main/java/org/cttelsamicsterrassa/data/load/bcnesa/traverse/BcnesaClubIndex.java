package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineups;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A licence-to-club map for one {@code (season, league-competition, group)} folder, used to attribute
 * the clubs of a BCNESA fixture whose file does not name them directly.
 *
 * <h2>Why this exists</h2>
 * <p>A BCNESA match report covers a whole matchday of a group, not one match: it holds
 * every fixture played that day, but names the clubs of only the first one ({@code equipos}) and
 * lists the lineup of only the first one ({@code alineaciones}). Every other fixture in the file must
 * have its clubs inferred from its players.</p>
 *
 * <p>Every file in the group, including the one being resolved, contributes its header pairing
 * (club name to each licence in its {@code alineaciones}) as one vote. Measured over the whole BCNESA
 * export, this resolves 94.8% of fixtures; the 19 of 7,975 licences that vote for more than one club
 * (a player who changed club mid-season within the group) are settled by majority.</p>
 */
public final class BcnesaClubIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaClubIndex.class);

    private static final Pattern MATCH_REPORT_FILE_PATTERN = Pattern.compile("acta.*\\.json");

    private final Map<String, Map<String, Integer>> votesByLicense;

    private BcnesaClubIndex(Map<String, Map<String, Integer>> votesByLicense) {
        this.votesByLicense = votesByLicense;
    }

    /**
     * Builds the index for one group folder by reading every match report under it, recursively
     * through its phase subfolders.
     */
    public static BcnesaClubIndex build(Path groupFolder, ActaParser actaParser) throws IOException {
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        for (Path phaseFolder : listDirectories(groupFolder)) {
            for (Path reportFile : listJsonFiles(phaseFolder)) {
                Acta acta;
                try {
                    acta = actaParser.parse(reportFile);
                } catch (ActaParseException e) {
                    LOGGER.debug("Club index pre-pass skipping unreadable {}: {}", reportFile, e.getMessage());
                    continue;
                }
                register(votes, acta);
            }
        }
        return new BcnesaClubIndex(votes);
    }

    /**
     * Builds an index directly from a licence-to-club map, one vote each. Exists for tests that need
     * a controllable index without parsing files.
     */
    public static BcnesaClubIndex of(Map<String, String> clubByLicense) {
        Map<String, Map<String, Integer>> votes = new HashMap<>();
        clubByLicense.forEach((license, club) -> votes.put(license, new HashMap<>(Map.of(club, 1))));
        return new BcnesaClubIndex(votes);
    }

    private static void register(Map<String, Map<String, Integer>> votes, Acta acta) {
        ActaTeams teams = acta.teams();
        ActaLineups lineups = acta.lineups();
        if (teams == null || lineups == null) {
            return;
        }
        registerSide(votes, lineups.home(), teams.home() != null ? teams.home().name() : null);
        registerSide(votes, lineups.away(), teams.away() != null ? teams.away().name() : null);
    }

    private static void registerSide(Map<String, Map<String, Integer>> votes,
                                     Map<String, ActaLineupPlayer> side,
                                     String clubName) {
        if (clubName == null) {
            return;
        }
        for (ActaLineupPlayer player : side.values()) {
            if (player == null || player.license() == null) {
                continue;
            }
            votes.computeIfAbsent(player.license(), k -> new HashMap<>())
                    .merge(clubName, 1, Integer::sum);
        }
    }

    /**
     * Resolves a club name from the licences of a fixture's own participants: each licence votes for
     * its most-seen club, and the majority among those votes wins. Empty when none of the licences
     * are in the index.
     */
    public Optional<String> resolve(Collection<String> licenses) {
        Map<String, Integer> tally = new HashMap<>();
        for (String license : licenses) {
            Map<String, Integer> clubVotes = votesByLicense.get(license);
            if (clubVotes == null) {
                continue;
            }
            topVote(clubVotes).ifPresent(club -> tally.merge(club, 1, Integer::sum));
        }
        return topVote(tally);
    }

    private static Optional<String> topVote(Map<String, Integer> tally) {
        return tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    private static List<Path> listDirectories(Path folder) throws IOException {
        return list(folder, Files::isDirectory);
    }

    /**
     * The same opaque {@code acta*.json} rule the navigator walks with, so the pre-pass votes on
     * exactly the files that will later be dispatched.
     */
    private static List<Path> listJsonFiles(Path folder) throws IOException {
        return list(folder, path -> Files.isRegularFile(path)
                && MATCH_REPORT_FILE_PATTERN.matcher(path.getFileName().toString()).matches());
    }

    private static List<Path> list(Path folder, java.util.function.Predicate<Path> accepted) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                if (accepted.test(entry)) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }
}
