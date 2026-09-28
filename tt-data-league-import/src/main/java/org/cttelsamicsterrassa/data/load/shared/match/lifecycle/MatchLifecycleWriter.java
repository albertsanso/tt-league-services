package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The shared create/upgrade/reschedule/skip decision of the incremental actas import
 * (FEAT-00081, analysis section 4.3), applied over the per-source {@link MatchLifecycleSource}.
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
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(source, "source");

        return switch (classification.completeness()) {
            case PLAYED -> applyPlayed(existing, source);
            case PENDING -> applyPending(existing, source);
            case PARTIAL -> applyScheduledBranch(existing, source, MatchLifecycleOutcome.PARTIAL_REPORTED);
            case INVALID -> applyScheduledBranch(existing, source, MatchLifecycleOutcome.INVALID_REPORTED);
        };
    }

    private MatchLifecycleOutcome applyPlayed(Optional<Match> existing, MatchLifecycleSource source) {
        if (existing.isEmpty()) {
            MatchContent content = source.buildPlayedContent(UUID.randomUUID(), false);
            matchRepository.saveMatch(content.match());
            lineupRepository.saveLineups(content.lineups());
            gameRepository.saveGames(content.games());
            if (setScoreRepository != null) {
                setScoreRepository.saveSetScores(content.setScores());
            }
            doublesPairRepository.saveDoublesPairs(content.doublesPairs());
            return MatchLifecycleOutcome.PLAYED_CREATED;
        }
        Match stored = existing.get();
        if (stored.isPlayed()) {
            return MatchLifecycleOutcome.PLAYED_KEPT;
        }
        matchRepository.replaceMatchContent(source.buildPlayedContent(stored.getId(), true));
        return MatchLifecycleOutcome.UPGRADED_TO_PLAYED;
    }

    private MatchLifecycleOutcome applyPending(Optional<Match> existing, MatchLifecycleSource source) {
        if (existing.isEmpty()) {
            matchRepository.saveMatch(source.buildScheduledMatch(UUID.randomUUID()));
            return MatchLifecycleOutcome.SCHEDULED_CREATED;
        }
        Match stored = existing.get();
        if (stored.isPlayed()) {
            return MatchLifecycleOutcome.REGRESSION_REPORTED;
        }
        return reschedule(stored, source);
    }

    private MatchLifecycleOutcome applyScheduledBranch(Optional<Match> existing,
                                                       MatchLifecycleSource source,
                                                       MatchLifecycleOutcome reportOutcome) {
        if (existing.isEmpty()) {
            matchRepository.saveMatch(source.buildScheduledMatch(UUID.randomUUID()));
            return reportOutcome;
        }
        Match stored = existing.get();
        if (stored.isPlayed()) {
            return reportOutcome == MatchLifecycleOutcome.PARTIAL_REPORTED
                    ? MatchLifecycleOutcome.REGRESSION_REPORTED
                    : MatchLifecycleOutcome.INVALID_REPORTED;
        }
        reschedule(stored, source);
        return reportOutcome;
    }

    /**
     * The reschedule rule: the incoming schedule comes from the SCHEDULED header, a {@code null}
     * incoming component keeps the stored value, and {@code updateSchedule} runs only when the
     * merged schedule differs from the stored one, which keeps re-imports idempotent.
     */
    private MatchLifecycleOutcome reschedule(Match stored, MatchLifecycleSource source) {
        Match incoming = source.buildScheduledMatch(stored.getId());
        MatchSchedule storedSchedule = new MatchSchedule(stored.getDateTime(), stored.getCity(),
                stored.getVenue(), stored.getRefereeName(), stored.getRefereeLicense());
        MatchSchedule merged = new MatchSchedule(
                incoming.getDateTime() != null ? incoming.getDateTime() : storedSchedule.dateTime(),
                incoming.getCity() != null ? incoming.getCity() : storedSchedule.city(),
                incoming.getVenue() != null ? incoming.getVenue() : storedSchedule.venue(),
                incoming.getRefereeName() != null ? incoming.getRefereeName() : storedSchedule.refereeName(),
                incoming.getRefereeLicense() != null ? incoming.getRefereeLicense() : storedSchedule.refereeLicense());
        if (merged.equals(storedSchedule)) {
            return MatchLifecycleOutcome.UNCHANGED;
        }
        matchRepository.updateSchedule(stored.getId(), merged);
        return MatchLifecycleOutcome.RESCHEDULED;
    }
}
