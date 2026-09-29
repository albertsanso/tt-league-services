package org.cttelsamicsterrassa.data.load.shared.match.lifecycle;

import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;

import java.util.Objects;
import java.util.Optional;

/**
 * The pure create/upgrade/reschedule/skip decision of the incremental actas import
 * (FEAT-00081, analysis section 4.3), extracted from {@link MatchLifecycleWriter} so the preview
 * can reuse exactly the same rules without writing anything (FEAT-00088).
 *
 * <p>Decision table for a fixture whose stored match is {@code existing}:</p>
 * <table border="1">
 *   <caption>Plan per classification and stored status</caption>
 *   <tr><th>existing</th><th>PLAYED</th><th>PENDING</th><th>PARTIAL</th><th>INVALID</th></tr>
 *   <tr><td>none</td><td>CREATE_PLAYED</td><td>CREATE_SCHEDULED</td>
 *       <td>CREATE_SCHEDULED, report</td><td>CREATE_SCHEDULED, report</td></tr>
 *   <tr><td>SCHEDULED</td><td>UPGRADE_TO_PLAYED</td><td>reschedule rule</td>
 *       <td>reschedule rule, report</td><td>reschedule rule, report</td></tr>
 *   <tr><td>PLAYED</td><td>NONE (keep)</td><td>NONE, report regression</td>
 *       <td>NONE, report regression</td><td>NONE, report invalid</td></tr>
 * </table>
 *
 * <p>Stateless and repository-free. It calls {@code source.buildScheduledMatch(stored.getId())}
 * only when a stored SCHEDULED match exists (the reschedule rule needs the incoming schedule) and
 * never {@code buildPlayedContent}, so planning is cheap and side-effect free.</p>
 */
public final class MatchLifecyclePlanner {

    /**
     * Plans one fixture. The reschedule rule: the incoming schedule comes from the SCHEDULED
     * header, a {@code null} incoming component keeps the stored value, and the plan asks for
     * {@link MatchLifecycleAction#UPDATE_SCHEDULE} only when the merged schedule differs from the
     * stored one, which keeps re-imports idempotent.
     */
    public MatchLifecyclePlan plan(ActaClassification classification,
                                   Optional<Match> existing,
                                   MatchLifecycleSource source) {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(source, "source");

        return switch (classification.completeness()) {
            case PLAYED -> planPlayed(existing);
            case PENDING -> planPending(existing, source);
            case PARTIAL -> planReported(existing, source, MatchLifecycleOutcome.PARTIAL_REPORTED);
            case INVALID -> planReported(existing, source, MatchLifecycleOutcome.INVALID_REPORTED);
        };
    }

    /**
     * The plan for a fixture whose match is not stored yet, without a source: the real run
     * registers the teams before the match processor, so an unregistered team in the preview
     * still means creation (FEAT-00088).
     */
    public static MatchLifecyclePlan planCreation(ActaClassification classification) {
        Objects.requireNonNull(classification, "classification");
        return switch (classification.completeness()) {
            case PLAYED -> MatchLifecyclePlan.of(MatchLifecycleOutcome.PLAYED_CREATED,
                    MatchLifecycleAction.CREATE_PLAYED);
            case PENDING -> MatchLifecyclePlan.of(MatchLifecycleOutcome.SCHEDULED_CREATED,
                    MatchLifecycleAction.CREATE_SCHEDULED);
            case PARTIAL -> MatchLifecyclePlan.of(MatchLifecycleOutcome.PARTIAL_REPORTED,
                    MatchLifecycleAction.CREATE_SCHEDULED);
            case INVALID -> MatchLifecyclePlan.of(MatchLifecycleOutcome.INVALID_REPORTED,
                    MatchLifecycleAction.CREATE_SCHEDULED);
        };
    }

    private static MatchLifecyclePlan planPlayed(Optional<Match> existing) {
        if (existing.isEmpty()) {
            return MatchLifecyclePlan.of(MatchLifecycleOutcome.PLAYED_CREATED,
                    MatchLifecycleAction.CREATE_PLAYED);
        }
        if (existing.get().isPlayed()) {
            return MatchLifecyclePlan.of(MatchLifecycleOutcome.PLAYED_KEPT, MatchLifecycleAction.NONE);
        }
        return MatchLifecyclePlan.of(MatchLifecycleOutcome.UPGRADED_TO_PLAYED,
                MatchLifecycleAction.UPGRADE_TO_PLAYED);
    }

    private static MatchLifecyclePlan planPending(Optional<Match> existing, MatchLifecycleSource source) {
        if (existing.isEmpty()) {
            return MatchLifecyclePlan.of(MatchLifecycleOutcome.SCHEDULED_CREATED,
                    MatchLifecycleAction.CREATE_SCHEDULED);
        }
        Match stored = existing.get();
        if (stored.isPlayed()) {
            return MatchLifecyclePlan.of(MatchLifecycleOutcome.REGRESSION_REPORTED, MatchLifecycleAction.NONE);
        }
        return planReschedule(stored, source, MatchLifecycleOutcome.RESCHEDULED,
                MatchLifecycleOutcome.UNCHANGED);
    }

    private static MatchLifecyclePlan planReported(Optional<Match> existing,
                                                   MatchLifecycleSource source,
                                                   MatchLifecycleOutcome reportOutcome) {
        if (existing.isEmpty()) {
            return MatchLifecyclePlan.of(reportOutcome, MatchLifecycleAction.CREATE_SCHEDULED);
        }
        Match stored = existing.get();
        if (stored.isPlayed()) {
            return MatchLifecyclePlan.of(
                    reportOutcome == MatchLifecycleOutcome.PARTIAL_REPORTED
                            ? MatchLifecycleOutcome.REGRESSION_REPORTED
                            : MatchLifecycleOutcome.INVALID_REPORTED,
                    MatchLifecycleAction.NONE);
        }
        return planReschedule(stored, source, reportOutcome, reportOutcome);
    }

    private static MatchLifecyclePlan planReschedule(Match stored,
                                                     MatchLifecycleSource source,
                                                     MatchLifecycleOutcome changedOutcome,
                                                     MatchLifecycleOutcome unchangedOutcome) {
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
            return MatchLifecyclePlan.of(unchangedOutcome, MatchLifecycleAction.NONE);
        }
        return MatchLifecyclePlan.reschedule(changedOutcome, merged);
    }
}
