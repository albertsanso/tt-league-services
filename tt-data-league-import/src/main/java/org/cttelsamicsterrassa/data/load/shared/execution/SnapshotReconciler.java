package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Reports stored SCHEDULED matches that a snapshot run did not see (source task T10; analysis
 * section 4.7, risk K8; FEAT-00086). Every run is a snapshot run today: the season folder is
 * traversed as a whole, so a stored SCHEDULED fixture that the snapshot no longer carries has
 * probably been cancelled or moved by the federation.
 *
 * <p>The reconciler is strictly report-only: it reads the stored SCHEDULED matches of the source and
 * season once and turns each absent one into a warning. It never deletes, re-keys, re-statuses or
 * reschedules a match; deletion of a vanished fixture stays a manual decision. A match counts as
 * seen when its {@code id_partido} (source fixture id) or its natural key appears in the run's
 * {@link SnapshotFixtures}.</p>
 *
 * <p><b>Window rule.</b> FCTT publishes a sliding window of jornadas, so the snapshot's highest round
 * is a moving frontier: an unseen match is flagged only when its round is at or below the highest
 * round the snapshot saw for that competition/group/phase scope. Rounds beyond it are upcoming
 * fixtures the window has not reached yet and are never flagged. When a whole scope vanished from the
 * snapshot, the snapshot-wide highest round is used instead, so a disappeared group is still reported
 * unless all its rounds lie beyond the frontier.</p>
 */
public final class SnapshotReconciler {

    private static final String PROCESSOR = "SnapshotReconciliation";

    private final MatchRepository matchRepository;

    public SnapshotReconciler(MatchRepository matchRepository) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
    }

    /**
     * Compares the stored SCHEDULED matches of one source and season against the fixtures the run
     * saw. Returns one deterministic warning per absent match, in competition/group/phase/round/home
     * order; an empty list when nothing is absent or the run saw no fixture at all (in which case the
     * repository is not even read).
     */
    public List<ImportExecutionIssue> reconcile(ImportSource source, Season season, SnapshotFixtures seen) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(seen, "seen");
        if (seen.isEmpty()) {
            return List.of();
        }

        List<Match> stored = matchRepository.findMatchesBySourceSeasonAndStatus(source, season,
                MatchStatus.SCHEDULED);
        List<Match> absent = new ArrayList<>();
        for (Match match : stored) {
            if (isSeen(match, seen) || !isWithinWindow(match, seen)) {
                continue;
            }
            absent.add(match);
        }
        absent.sort(SnapshotReconciler::compare);
        List<ImportExecutionIssue> issues = new ArrayList<>(absent.size());
        for (Match match : absent) {
            issues.add(new ImportExecutionIssue(PROCESSOR, "match " + match.getId(), reason(season, match)));
        }
        return issues;
    }

    private static boolean isSeen(Match match, SnapshotFixtures seen) {
        return seen.containsFixtureId(match.getSourceFixtureId()) || seen.containsNaturalKey(match);
    }

    private static boolean isWithinWindow(Match match, SnapshotFixtures seen) {
        OptionalInt limit = seen.highestRound(match.getCompetition(), match.getGroupNumber(), match.getPhase());
        if (limit.isEmpty()) {
            limit = seen.highestRoundOverall();
        }
        return limit.isPresent() && match.getRound() <= limit.getAsInt();
    }

    private static String reason(Season season, Match match) {
        return "Stored SCHEDULED match absent from the " + season + " snapshot: "
                + match.getCompetition() + " G" + match.getGroupNumber() + " "
                + (match.getPhase() == null ? "-" : match.getPhase()) + " round " + match.getRound() + ", "
                + nameOf(match.getHomeTeam()) + " vs " + nameOf(match.getAwayTeam())
                + ", id_partido " + (match.getSourceFixtureId() == null ? "none" : match.getSourceFixtureId())
                + "; kept, not deleted";
    }

    private static String nameOf(Team team) {
        return team == null || team.getName() == null ? "?" : team.getName();
    }

    private static int compare(Match left, Match right) {
        return Comparator
                .comparing(Match::getCompetition, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Match::getGroupNumber, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Match::getPhase, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(Match::getRound)
                .thenComparing(match -> nameOfKey(match.getHomeTeam()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(left, right);
    }

    private static String nameOfKey(Team team) {
        return team == null ? null : team.getName();
    }
}
