package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes the shared create/upgrade/reschedule/skip decision of the incremental actas import
 * (FEAT-00081, analysis section 4.3) over the per-source {@link MatchLifecycleSource}. The
 * decision itself lives in {@link MatchLifecyclePlanner} so the preview can reuse it without
 * writing anything (FEAT-00088); this writer plans, then executes the planned action.
 *
 * <p>Decision table for a fixture whose stored match is {@code existing}:</p>
 * <table border="1">
 *   <caption>Outcome per classification and stored status</caption>
 *   <tr><th>existing</th><th>PLAYED</th><th>PENDING</th><th>PARTIAL</th><th>INVALID</th></tr>
 *   <tr><td>none</td><td>save played content</td><td>save scheduled header</td>
 *       <td>save scheduled header, report</td><td>save scheduled header, report</td></tr>
 *   <tr><td>SCHEDULED</td><td>replaceMatchContent (upgrade)</td><td>reschedule rule</td>
 *       <td>reschedule rule, report</td><td>reschedule rule, report</td></tr>
 *   <tr><td>PLAYED</td><td>keep / amend (FEAT-00089)</td><td>report regression</td>
 *       <td>report regression</td><td>report invalid</td></tr>
 * </table>
 *
 * <p>Every acta that is not played yet is stored as a SCHEDULED match (2026-09-28 user direction),
 * including INVALID ones; a PLAYED match is never downgraded here. A reportable outcome (regression,
 * partial, invalid, amendment) is returned, not thrown: genuine repository failures propagate to the
 * navigator's existing per-processor failure handling. An unresolved pending fixture (no team names,
 * hence no natural key) never reaches this writer; callers skip and report it before dispatch.</p>
 *
 * <p>FEAT-00089: every PLAYED match created or upgraded here stores the {@link MatchContentChecksum}
 * of the content actually written, whatever the mode, so a later opt-in has a baseline. When amended
 * detection is enabled ({@code mode != null}) and a stored PLAYED match is seen again, the writer
 * computes the incoming checksum and lets {@link MatchLifecyclePlanner#planAmendment} decide whether
 * to re-apply, adopt the checksum as a baseline, or nothing. Report mode performs the same detection
 * without any write.</p>
 *
 * <p>Plain class constructed by each match processor with its repositories; not a Spring bean and
 * stateless apart from those references.</p>
 */
public final class MatchLifecycleWriter {

    /** Dedicated audit logger so an amendment can be watched independently of the run log (FEAT-00089). */
    private static final Logger AMENDED_ACTA_AUDIT =
            LoggerFactory.getLogger("org.cttelsamicsterrassa.data.load.audit.AmendedActa");

    private final MatchRepository matchRepository;
    private final LineupRepository lineupRepository;
    private final GameRepository gameRepository;
    private final SetScoreRepository setScoreRepository;
    private final DoublesPairRepository doublesPairRepository;
    private final MatchLifecyclePlanner planner = new MatchLifecyclePlanner();

    public MatchLifecycleWriter(MatchRepository matchRepository,
                                LineupRepository lineupRepository,
                                GameRepository gameRepository,
                                SetScoreRepository setScoreRepository,
                                DoublesPairRepository doublesPairRepository) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.lineupRepository = Objects.requireNonNull(lineupRepository, "lineupRepository");
        this.gameRepository = Objects.requireNonNull(gameRepository, "gameRepository");
        this.setScoreRepository = Objects.requireNonNull(setScoreRepository, "setScoreRepository");
        this.doublesPairRepository = Objects.requireNonNull(doublesPairRepository, "doublesPairRepository");
    }

    /** Current behaviour, byte-for-byte: amended-acta detection is disabled. */
    public MatchLifecycleOutcome apply(ActaClassification classification,
                                       Optional<Match> existing,
                                       MatchLifecycleSource source) {
        return apply(classification, existing, source, null, null);
    }

    /**
     * @param mode     the amended-acta mode, or {@code null} when detection is disabled
     * @param location the report file the acta came from, used only for the audit line
     */
    public MatchLifecycleOutcome apply(ActaClassification classification,
                                       Optional<Match> existing,
                                       MatchLifecycleSource source,
                                       AmendedActaMode mode,
                                       Path location) {
        MatchLifecyclePlan plan = planner.plan(classification, existing, source);
        switch (plan.action()) {
            case CREATE_PLAYED -> createPlayed(withChecksum(source.buildPlayedContent(UUID.randomUUID(), false)));
            case CREATE_SCHEDULED -> matchRepository.saveMatch(source.buildScheduledMatch(UUID.randomUUID()));
            case UPGRADE_TO_PLAYED -> matchRepository.replaceMatchContent(withChecksum(
                    keepStoredFixtureId(source.buildPlayedContent(existing.get().getId(), true), existing.get())));
            case UPDATE_SCHEDULE -> matchRepository.updateSchedule(existing.get().getId(), plan.mergedSchedule());
            case NONE -> {
                if (mode != null && plan.outcome() == MatchLifecycleOutcome.PLAYED_KEPT) {
                    return applyAmendment(existing.orElseThrow(), source, mode, location);
                }
            }
            case REAPPLY_PLAYED, RECORD_SOURCE_CHECKSUM -> throw new IllegalStateException(
                    "An amended-acta action is only produced by planAmendment: " + plan.action());
        }
        return plan.outcome();
    }

    private void createPlayed(MatchContent content) {
        matchRepository.saveMatch(content.match());
        lineupRepository.saveLineups(content.lineups());
        gameRepository.saveGames(content.games());
        setScoreRepository.saveSetScores(content.setScores());
        doublesPairRepository.saveDoublesPairs(content.doublesPairs());
    }

    /**
     * Amended-acta detection for a stored PLAYED match (FEAT-00089): builds the incoming content
     * exactly as an upgrade would, computes its checksum, and lets the planner decide between
     * re-applying, adopting the checksum as a baseline, or nothing. Report mode writes nothing.
     */
    private MatchLifecycleOutcome applyAmendment(Match stored, MatchLifecycleSource source,
                                                 AmendedActaMode mode, Path location) {
        MatchContent content = withChecksum(keepStoredFixtureId(
                source.buildPlayedContent(stored.getId(), true), stored));
        String incomingChecksum = content.match().getSourceChecksum();
        MatchLifecyclePlan plan = planner.planAmendment(stored, incomingChecksum, mode);
        switch (plan.action()) {
            case REAPPLY_PLAYED -> matchRepository.replaceMatchContent(content);
            case RECORD_SOURCE_CHECKSUM -> matchRepository.recordSourceChecksum(stored.getId(), incomingChecksum);
            case NONE -> {
            }
            default -> throw new IllegalStateException(
                    "planAmendment produced an unexpected action: " + plan.action());
        }
        logAmendment(plan, mode, location, stored, content.match(), incomingChecksum);
        return plan.outcome();
    }

    /**
     * The checksum of the content actually written, on the header (FEAT-00089). It is computed from
     * the content built <em>after</em> {@link #keepStoredFixtureId}, so a stored fixture id is part
     * of the baseline exactly as it will be stored.
     */
    private static MatchContent withChecksum(MatchContent content) {
        String checksum = MatchContentChecksum.of(content);
        return new MatchContent(content.match().withSourceChecksum(checksum), content.lineups(),
                content.games(), content.setScores(), content.doublesPairs());
    }

    private static void logAmendment(MatchLifecyclePlan plan, AmendedActaMode mode, Path location,
                                     Match stored, Match header, String incomingChecksum) {
        if (plan.outcome() == MatchLifecycleOutcome.PLAYED_AMENDED
                || plan.outcome() == MatchLifecycleOutcome.PLAYED_AMENDMENT_REPORTED) {
            AMENDED_ACTA_AUDIT.info(
                    "amended-acta mode={} source={} season={} competition={} group={} round={} phase={} "
                            + "fixture={} match={} location={} checksum={} -> {}",
                    mode, header.getSource(), header.getSeason(), header.getCompetition(),
                    header.getGroupNumber(), header.getRound(), header.getPhase(),
                    dash(header.getSourceFixtureId()), stored.getId(), dash(location),
                    dash(stored.getSourceChecksum()), incomingChecksum);
        } else if (plan.action() == MatchLifecycleAction.RECORD_SOURCE_CHECKSUM) {
            AMENDED_ACTA_AUDIT.debug("adopted-acta-checksum match={} mode={} checksum={} -> {}",
                    stored.getId(), mode, dash(stored.getSourceChecksum()), incomingChecksum);
        }
    }

    private static String dash(Object value) {
        return value == null ? "-" : value.toString();
    }

    /**
     * An upgrade never erases a stored fixture id: when the incoming PLAYED header has no
     * {@code sourceFixtureId} but the stored SCHEDULED match has one, the rebuilt header carries
     * the stored value (FEAT-00083). A mismatch of two non-null ids can no longer reach this
     * writer: the {@link MatchFixtureIdentityGuard} reports it as a conflict beforehand
     * (FEAT-00085), so the only incoming value that gets through here is {@code null} or the
     * stored id itself.
     */
    private static MatchContent keepStoredFixtureId(MatchContent content, Match stored) {
        if (content.match().getSourceFixtureId() == null && stored.getSourceFixtureId() != null) {
            return new MatchContent(
                    content.match().withSourceFixtureId(stored.getSourceFixtureId()),
                    content.lineups(),
                    content.games(),
                    content.setScores(),
                    content.doublesPairs());
        }
        return content;
    }

}