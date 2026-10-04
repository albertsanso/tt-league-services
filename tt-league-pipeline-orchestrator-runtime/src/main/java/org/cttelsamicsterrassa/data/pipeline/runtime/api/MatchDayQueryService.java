package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the match-day API; uses the core repository port only, in one read-only transaction per call. */
@Service
@Transactional(readOnly = true)
public class MatchDayQueryService {

    private final MatchDayRepository repository;

    MatchDayQueryService(MatchDayRepository repository) {
        this.repository = repository;
    }

    public PageDto<MatchDaySummaryDto> list(MatchDayQuery query) {
        MatchDayPage page = repository.query(query);
        int totalPages = (int) Math.ceil((double) page.total() / page.size());
        return new PageDto<>(page.items().stream().map(MatchDaySummaryDto::from).toList(), page.page(), page.size(),
                page.total(), totalPages);
    }

    public Optional<MatchDayDetailDto> detail(UUID matchDayId) {
        return repository.findById(matchDayId).map(this::detail);
    }

    private MatchDayDetailDto detail(MatchDay day) {
        List<MatchTracking> matches = repository.findMatches(Set.of(day.id())).stream()
                .sorted(java.util.Comparator
                        .comparing(MatchTracking::matchDateTime, java.util.Comparator.nullsLast(
                                java.util.Comparator.naturalOrder()))
                        .thenComparing(MatchTracking::matchId))
                .toList();
        Map<TrackedMatchStatus, Integer> counts = new EnumMap<>(TrackedMatchStatus.class);
        int ignored = 0;
        for (MatchTracking match : matches) {
            counts.merge(match.status(), 1, Integer::sum);
            if (match.isIgnored()) {
                ignored++;
            }
        }
        Map<String, Integer> named = new LinkedHashMap<>();
        for (TrackedMatchStatus status : TrackedMatchStatus.values()) {
            named.put(status.name(), counts.getOrDefault(status, 0));
        }
        return new MatchDayDetailDto(MatchDaySummaryDto.from(day, named, ignored),
                matches.stream().map(TrackedMatchDto::from).toList(),
                repository.findEvents(day.id()).stream().map(MatchDayEventDto::from).toList());
    }
}
