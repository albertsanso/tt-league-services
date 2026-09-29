package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;

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
 *   <tr><td>PLAYED</td><td>keep</td><td>report regression</td><td>report regression</td>
 *       <td>report invalid</td></tr>
 * </table>
 *
 * <p>Every acta that is not played yet is stored as a SCHEDULED match (2026-09-28 user direction),
 * including INVALID ones; a PLAYED match is never downgraded or rewritten here. A reportable
 * outcome (regression, partial, invalid) is returned, not thrown: genuine repository failures
 * propagate to the navigator's existing per-processor failure handling. An unresolved pending
 * fixture (no team names, hence no natural key) never reaches this writer; callers skip and report
 * it before dispatch.</p>
 *
 * <p>Plain class constructed by each match processor with its repositories; not a Spring bean and
 * stateless apart from those references.</p>
 */
public final class MatchLifecycleWriter {

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
        this.setScoreRepository = setScoreRepository;
        this.doublesPairRepository = Objects.requireNonNull(doublesPairRepository, "doublesPairRepository");
    }

    /**
     * BCNESA stores no set scores, so its writers are built without a {@link SetScoreRepository}.
     */
    public MatchLifecycleWriter(MatchRepository matchRepository,
                                LineupRepository lineupRepository,
                                GameRepository gameRepository,
                                DoublesPairRepository doublesPairRepository) {
        this(matchRepository, lineupRepository, gameRepository, null, doublesPairRepository);
    }

    public MatchLifecycleOutcome apply(ActaClassification classification,
                                       Optional<Match> existing,
                                       MatchLifecycleSource source) {
        MatchLifecyclePlan plan = planner.plan(classification, existing, source);
        switch (plan.action()) {
            case CREATE_PLAYED -> createPlayed(source);
            case CREATE_SCHEDULED -> matchRepository.saveMatch(source.buildScheduledMatch(UUID.randomUUID()));
            case UPGRADE_TO_PLAYED -> matchRepository.replaceMatchContent(
                    keepStoredFixtureId(source.buildPlayedContent(existing.get().getId(), true), existing.get()));
            case UPDATE_SCHEDULE -> matchRepository.updateSchedule(existing.get().getId(), plan.mergedSchedule());
            case NONE -> {
            }
        }
        return plan.outcome();
    }

    private void createPlayed(MatchLifecycleSource source) {
        MatchContent content = source.buildPlayedContent(UUID.randomUUID(), false);
        matchRepository.saveMatch(content.match());
        lineupRepository.saveLineups(content.lineups());
        gameRepository.saveGames(content.games());
        if (setScoreRepository != null) {
            setScoreRepository.saveSetScores(content.setScores());
        }
        doublesPairRepository.saveDoublesPairs(content.doublesPairs());
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
