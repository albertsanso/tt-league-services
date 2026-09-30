package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarStateResolver;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared assembly of calendar match rows (FEAT-00092, FEAT-00093): loads the manual overdue marks
 * with a single query, resolves the calendar state through {@link CalendarStateResolver} and builds
 * the {@link CalendarMatchReadModel}. Both calendar handlers use it so the state rule and the row
 * shape stay identical between the jornada view and the date-range view.
 */
public final class CalendarMatchAssembler {

    private final MatchOverdueMarkRepository marks;
    private final OverdueGracePeriod grace;

    public CalendarMatchAssembler(MatchOverdueMarkRepository marks, OverdueGracePeriod grace) {
        this.marks = marks;
        this.grace = grace;
    }

    /** One {@code findByMatchIds} call, restricted to the SCHEDULED ids. */
    public Map<UUID, MatchOverdueMark> marksFor(List<Match> matches) {
        return marks.findByMatchIds(matches.stream()
                        .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                        .map(Match::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(MatchOverdueMark::matchId, Function.identity()));
    }

    public CalendarMatchState state(Match match, Integer currentRound, Map<UUID, MatchOverdueMark> marks,
                                    LocalDate today) {
        boolean marked = match.getStatus() == MatchStatus.SCHEDULED && marks.containsKey(match.getId());
        return CalendarStateResolver.resolve(match, currentRound, marked, today, grace);
    }

    public CalendarMatchReadModel toReadModel(Match match, CalendarMatchState state,
                                              Map<UUID, MatchOverdueMark> marks, LocalDate today) {
        MatchOverdueMark mark = marks.get(match.getId());
        boolean marked = match.getStatus() == MatchStatus.SCHEDULED && mark != null;
        return new CalendarMatchReadModel(
                match.getId(), match.getDateTime(), match.getCity(), match.getVenue(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getName(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getName(),
                match.getWinnerTeam() == null ? null : match.getWinnerTeam().getName(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), match.getStatus(), state,
                marked, marked ? mark.markedAt() : null, marked ? mark.markedBy() : null,
                match.getCompetition(), match.getGroupNumber(), match.getPhase(), match.getRound(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getId(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getId(),
                CalendarStateResolver.canMarkOverdue(match, today));
    }
}
