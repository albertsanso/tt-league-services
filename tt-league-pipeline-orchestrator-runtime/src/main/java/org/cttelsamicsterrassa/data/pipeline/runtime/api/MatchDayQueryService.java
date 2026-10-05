package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEvent;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayPage;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDaySummary;
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
    private final PipelineRunRepository runRepository;
    private final RunDtoMapper mapper;

    MatchDayQueryService(MatchDayRepository repository, PipelineRunRepository runRepository, RunDtoMapper mapper) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.mapper = mapper;
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

    public MatchDayFacetsDto facets(PipelineSource source, String season) {
        return MatchDayFacetsDto.from(repository.facets(source, season));
    }

    private MatchDayDetailDto detail(MatchDay day) {
        List<MatchTracking> matches = repository.findMatches(Set.of(day.id())).stream()
                .sorted(Comparator
                        .comparing(MatchTracking::matchDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MatchTracking::matchId))
                .toList();
        Map<TrackedMatchStatus, Integer> counts = new EnumMap<>(TrackedMatchStatus.class);
        Map<TrackedMatchStatus, Integer> ignored = new EnumMap<>(TrackedMatchStatus.class);
        Set<UUID> runIds = new LinkedHashSet<>();
        for (MatchTracking match : matches) {
            counts.merge(match.status(), 1, Integer::sum);
            if (match.isIgnored()) {
                ignored.merge(match.status(), 1, Integer::sum);
            }
            if (match.reportedRunId() != null) {
                runIds.add(match.reportedRunId());
            }
        }
        List<MatchDayEvent> events = repository.findEvents(day.id());
        events.stream().map(MatchDayEvent::runId).filter(Objects::nonNull).forEach(runIds::add);
        List<RunSummaryDto> runs = runRepository.findByIds(runIds).stream()
                .sorted(Comparator.comparing(PipelineRun::createdAt).reversed().thenComparing(PipelineRun::id))
                .map(run -> mapper.summary(run, null))
                .toList();
        return new MatchDayDetailDto(MatchDaySummaryDto.from(new MatchDaySummary(day, counts, ignored)),
                matches.stream().map(TrackedMatchDto::from).toList(),
                events.stream().map(MatchDayEventDto::from).toList(), runs);
    }
}
