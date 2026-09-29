package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarRoundReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.SeasonCalendarReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarStateResolver;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the season calendar read model (FEAT-00092) from one all-status match read plus the manual
 * overdue marks. The jornada progress and the calendar states are never re-implemented here: this
 * handler only reuses {@link RoundProgressCalculator} and {@link CalendarStateResolver}.
 *
 * <p>Declared as a {@code @Bean} in the API runtime (not {@code @Named}) so the import runtime,
 * which scans every package, never needs the {@link OverdueGracePeriod} configuration.</p>
 */
public class FindSeasonCalendarQueryHandler
        extends DomainQueryHandler<FindSeasonCalendarQuery, SeasonCalendarReadModel> {

    private final MatchRepository matches;
    private final MatchOverdueMarkRepository marks;
    private final OverdueGracePeriod grace;
    private final Clock clock;

    public FindSeasonCalendarQueryHandler(MatchRepository matches, MatchOverdueMarkRepository marks,
                                          OverdueGracePeriod grace, Clock clock) {
        this.matches = matches;
        this.marks = marks;
        this.grace = grace;
        this.clock = clock;
    }

    @Override
    public DomainQueryResponse<SeasonCalendarReadModel> handle(FindSeasonCalendarQuery query) {
        try {
            return DomainQueryResponse.sucessResponse(build(query));
        } catch (IllegalArgumentException exception) {
            return DomainQueryResponse.failResponse(null);
        }
    }

    private SeasonCalendarReadModel build(FindSeasonCalendarQuery query) {
        List<Match> calendarMatches = matches.findMatchesBySourceSeasonAndCompetition(
                query.getSource(), query.getSeason(), query.getCompetition());

        List<RoundProgress> progress = RoundProgressCalculator.compute(query.getSource(), query.getSeason(),
                roundStatusCounts(query.getCompetition(), calendarMatches));

        Map<GroupKey, List<Match>> matchesByGroup = calendarMatches.stream()
                .collect(Collectors.groupingBy(m -> new GroupKey(m.getGroupNumber(), m.getPhase()),
                        LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        LocalDate today = LocalDate.now(clock.withZone(Match.COMPETITION_ZONE));

        Map<UUID, MatchOverdueMark> markByMatchId = marks.findByMatchIds(
                        calendarMatches.stream()
                                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                                .map(Match::getId)
                                .toList())
                .stream()
                .collect(Collectors.toMap(MatchOverdueMark::matchId, Function.identity()));

        List<CalendarGroupReadModel> groups = new ArrayList<>();
        for (RoundProgress row : progress) {
            if (query.getGroupNumber() != null && !Objects.equals(query.getGroupNumber(), row.groupNumber())) {
                continue;
            }
            List<Match> groupMatches = matchesByGroup.getOrDefault(
                    new GroupKey(row.groupNumber(), row.phase()), List.of());
            groups.add(buildGroup(query, row, groupMatches, markByMatchId, today));
        }
        return new SeasonCalendarReadModel(query.getSource(), query.getSeason(), query.getCompetition(),
                today, grace.days(), groups);
    }

    private CalendarGroupReadModel buildGroup(FindSeasonCalendarQuery query, RoundProgress row,
                                              List<Match> groupMatches,
                                              Map<UUID, MatchOverdueMark> markByMatchId, LocalDate today) {
        Integer currentRound = row.currentRound();
        Map<UUID, CalendarMatchState> stateByMatchId = new HashMap<>();
        Map<Integer, List<Match>> matchesByRound = new LinkedHashMap<>();

        long overdueMatches = 0;
        long postponedMatches = 0;
        for (Match match : groupMatches) {
            boolean marked = match.getStatus() == MatchStatus.SCHEDULED
                    && markByMatchId.containsKey(match.getId());
            CalendarMatchState state = CalendarStateResolver.resolve(match, currentRound, marked, today, grace);
            stateByMatchId.put(match.getId(), state);
            if (state == CalendarMatchState.OVERDUE) {
                overdueMatches++;
            } else if (state == CalendarMatchState.POSTPONED) {
                postponedMatches++;
            }
            matchesByRound.computeIfAbsent(match.getRound(), r -> new ArrayList<>()).add(match);
        }

        List<CalendarRoundReadModel> rounds = buildRounds(query, currentRound, matchesByRound,
                stateByMatchId, markByMatchId, today);

        return new CalendarGroupReadModel(row.groupNumber(), row.phase(), row.currentRound(),
                row.lastCompleteRound(), row.scheduledMatches(), row.playedMatches(),
                overdueMatches, postponedMatches, rounds);
    }

    private List<CalendarRoundReadModel> buildRounds(FindSeasonCalendarQuery query, Integer currentRound,
                                                     Map<Integer, List<Match>> matchesByRound,
                                                     Map<UUID, CalendarMatchState> stateByMatchId,
                                                     Map<UUID, MatchOverdueMark> markByMatchId,
                                                     LocalDate today) {
        List<Integer> rounds = new ArrayList<>(matchesByRound.keySet());
        rounds.sort(Integer::compareTo);

        List<CalendarRoundReadModel> models = new ArrayList<>();
        for (int round : rounds) {
            if (query.getRound() != null && query.getRound() != round) {
                continue;
            }
            models.add(buildRound(round, currentRound, matchesByRound.get(round), stateByMatchId,
                    markByMatchId, today));
        }
        return models;
    }

    private CalendarRoundReadModel buildRound(int round, Integer currentRound, List<Match> roundMatches,
                                              Map<UUID, CalendarMatchState> stateByMatchId,
                                              Map<UUID, MatchOverdueMark> markByMatchId, LocalDate today) {
        List<CalendarMatchReadModel> matches = roundMatches.stream()
                .sorted(Comparator.comparing(Match::getDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(m -> m.getHomeTeam() == null ? null : m.getHomeTeam().getName(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Match::getId))
                .map(m -> toMatchModel(m, stateByMatchId.get(m.getId()), markByMatchId.get(m.getId())))
                .toList();

        long scheduled = roundMatches.stream().filter(m -> m.getStatus() == MatchStatus.SCHEDULED).count();
        long played = roundMatches.size() - scheduled;
        boolean complete = scheduled == 0;
        boolean current = Objects.equals(currentRound, round);

        LocalDate firstDate = null;
        LocalDate lastDate = null;
        for (Match match : roundMatches) {
            if (match.getDateTime() == null) {
                continue;
            }
            LocalDate date = match.getDateTime().withZoneSameInstant(Match.COMPETITION_ZONE).toLocalDate();
            if (firstDate == null || date.isBefore(firstDate)) {
                firstDate = date;
            }
            if (lastDate == null || date.isAfter(lastDate)) {
                lastDate = date;
            }
        }

        return new CalendarRoundReadModel(round, firstDate, lastDate, scheduled, played, complete, current,
                matches);
    }

    private CalendarMatchReadModel toMatchModel(Match match, CalendarMatchState state,
                                                MatchOverdueMark mark) {
        boolean marked = match.getStatus() == MatchStatus.SCHEDULED && mark != null;
        return new CalendarMatchReadModel(
                match.getId(), match.getDateTime(), match.getCity(), match.getVenue(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getName(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getName(),
                match.getWinnerTeam() == null ? null : match.getWinnerTeam().getName(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), match.getStatus(), state,
                marked, marked ? mark.markedAt() : null, marked ? mark.markedBy() : null);
    }

    private List<RoundStatusCount> roundStatusCounts(String competition, List<Match> matches) {
        Map<GroupRoundStatusKey, Long> counts = new LinkedHashMap<>();
        for (Match match : matches) {
            counts.merge(new GroupRoundStatusKey(match.getGroupNumber(), match.getPhase(), match.getRound(),
                    match.getStatus()), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new RoundStatusCount(competition, entry.getKey().groupNumber(),
                        entry.getKey().phase(), entry.getKey().round(), entry.getKey().status(),
                        entry.getValue()))
                .toList();
    }

    private record GroupKey(Integer groupNumber, String phase) {
    }

    private record GroupRoundStatusKey(Integer groupNumber, String phase, int round, MatchStatus status) {
    }
}