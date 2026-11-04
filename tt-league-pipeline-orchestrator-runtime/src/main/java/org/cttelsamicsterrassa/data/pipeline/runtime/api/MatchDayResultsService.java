package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway;
import org.springframework.stereotype.Service;

/**
 * Reads the results of the matches of a match day from the platform calendar, on demand. The tracker never stores
 * results, so nothing is cached or logged here; a platform failure is reported, not hidden. The tracker is read through
 * the repository (its own short transactions) before the platform call, so no database transaction stays open during
 * the HTTP request.
 */
@Service
public class MatchDayResultsService {

    private final MatchDayRepository repository;
    private final PlatformMatchGateway platform;

    MatchDayResultsService(MatchDayRepository repository, PlatformMatchGateway platform) {
        this.repository = repository;
        this.platform = platform;
    }

    public Optional<MatchDayResultsDto> results(UUID matchDayId) {
        Optional<MatchDay> found = repository.findById(matchDayId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MatchDay day = found.get();
        Set<UUID> tracked = new HashSet<>();
        for (MatchTracking match : repository.findMatches(Set.of(day.id()))) {
            tracked.add(match.matchId());
        }
        PlatformCompetitionCalendar calendar;
        try {
            calendar = platform.competitionCalendar(day.key().source(), day.key().season(), day.key().competition());
        } catch (GatewayException e) {
            throw new PlatformUnavailableException(e.getMessage(), e);
        }
        List<MatchResultDto> results = calendar.matches().stream()
                .filter(match -> tracked.contains(match.id()))
                .map(match -> new MatchResultDto(match.id(), match.status(), match.homeGamesWon(),
                        match.awayGamesWon(), match.winnerTeamName()))
                .toList();
        return Optional.of(new MatchDayResultsDto(day.id(), calendar.today(), results));
    }
}
