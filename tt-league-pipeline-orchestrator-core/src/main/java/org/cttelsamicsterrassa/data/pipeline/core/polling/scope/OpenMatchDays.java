package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;

/**
 * The match days to poll, read from the tracker: {@code OPEN} days, and days closed as {@code ALL_RESOLVED} that still
 * hold a postponed match. Manually closed, removed and upcoming days are never polled. Candidate matches are the
 * non-ignored ones that are scheduled, awaiting a result, overdue or postponed; a day without any is left out.
 */
public final class OpenMatchDays {

    private static final Set<TrackedMatchStatus> CANDIDATE_STATUSES = EnumSet.of(TrackedMatchStatus.SCHEDULED,
            TrackedMatchStatus.AWAITING_RESULT, TrackedMatchStatus.OVERDUE, TrackedMatchStatus.POSTPONED);

    private OpenMatchDays() {
    }

    public static List<OpenMatchDay> load(MatchDayRepository repository, PipelineSource source, String season) {
        List<MatchDay> pollable = repository.findBySourceAndSeason(source, season).stream()
                .filter(OpenMatchDays::isPollable)
                .toList();
        if (pollable.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pollable.stream().map(MatchDay::id).toList();
        Map<UUID, List<MatchTracking>> byDay = repository.findMatches(ids).stream()
                .filter(match -> !match.isIgnored() && CANDIDATE_STATUSES.contains(match.status()))
                .collect(Collectors.groupingBy(MatchTracking::matchDayId));
        List<OpenMatchDay> result = new ArrayList<>();
        for (MatchDay day : pollable) {
            List<MatchTracking> candidates = byDay.getOrDefault(day.id(), List.of());
            if (!candidates.isEmpty()) {
                result.add(new OpenMatchDay(day.key(), candidates));
            }
        }
        return result;
    }

    private static boolean isPollable(MatchDay day) {
        return day.state() == MatchDayState.OPEN
                || (day.state() == MatchDayState.CLOSED && day.closeReason() == CloseReason.ALL_RESOLVED);
    }
}
