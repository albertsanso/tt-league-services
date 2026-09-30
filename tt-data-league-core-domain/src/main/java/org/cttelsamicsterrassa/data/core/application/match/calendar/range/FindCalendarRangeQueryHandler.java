package org.cttelsamicsterrassa.data.core.application.match.calendar.range;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.calendar.CalendarMatchAssembler;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeFacets;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarRangeReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.range.dto.CalendarTeamFacet;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
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
import java.util.TreeSet;
import java.util.UUID;

/**
 * Builds the date-range calendar read model (FEAT-00093). The current round of each group comes from
 * the whole-season {@link RoundProgress}, so a match gets the same calendar state as in the jornada
 * view; the state rule itself lives in {@link CalendarMatchAssembler}. Nothing is written.
 *
 * <p>Declared as a {@code @Bean} in the API runtime (not {@code @Named}), like
 * {@code FindSeasonCalendarQueryHandler}.</p>
 */
public class FindCalendarRangeQueryHandler
        extends DomainQueryHandler<FindCalendarRangeQuery, CalendarRangeReadModel> {

    private final MatchRepository matches;
    private final OverdueGracePeriod grace;
    private final Clock clock;
    private final CalendarMatchAssembler assembler;

    public FindCalendarRangeQueryHandler(MatchRepository matches, MatchOverdueMarkRepository marks,
                                         OverdueGracePeriod grace, Clock clock) {
        this.matches = matches;
        this.grace = grace;
        this.clock = clock;
        this.assembler = new CalendarMatchAssembler(marks, grace);
    }

    @Override
    public DomainQueryResponse<CalendarRangeReadModel> handle(FindCalendarRangeQuery query) {
        try {
            return DomainQueryResponse.sucessResponse(build(query));
        } catch (IllegalArgumentException exception) {
            return DomainQueryResponse.failResponse(null);
        }
    }

    private CalendarRangeReadModel build(FindCalendarRangeQuery query) {
        List<Match> rangeMatches = matches.findMatchesBySourceSeasonAndDateRange(
                query.getSource(), query.getSeason(), query.getFrom(), query.getTo());

        Map<GroupKey, Integer> currentRounds = new HashMap<>();
        for (RoundProgress row : matches.findRoundProgress(query.getSource(), query.getSeason())) {
            currentRounds.put(new GroupKey(row.competition(), row.groupNumber(), row.phase()),
                    row.currentRound());
        }

        CalendarRangeFacets facets = facets(query, rangeMatches);

        List<Match> filtered = rangeMatches.stream()
                .filter(m -> query.getCompetition() == null || query.getCompetition().equals(m.getCompetition()))
                .filter(m -> query.getGroupNumber() == null
                        || Objects.equals(query.getGroupNumber(), m.getGroupNumber()))
                .filter(m -> query.getTeamId() == null || hasTeam(m, query.getTeamId()))
                .toList();

        LocalDate today = LocalDate.now(clock.withZone(Match.COMPETITION_ZONE));
        Map<UUID, MatchOverdueMark> markByMatchId = assembler.marksFor(filtered);

        List<CalendarMatchReadModel> models = filtered.stream()
                .sorted(matchOrder())
                .map(m -> assembler.toReadModel(m,
                        assembler.state(m,
                                currentRounds.get(new GroupKey(m.getCompetition(), m.getGroupNumber(),
                                        m.getPhase())),
                                markByMatchId, today),
                        markByMatchId, today))
                .toList();

        return new CalendarRangeReadModel(query.getSource(), query.getSeason(), query.getFrom(),
                query.getTo(), today, grace.days(), models, facets);
    }

    private static boolean hasTeam(Match match, UUID teamId) {
        return (match.getHomeTeam() != null && teamId.equals(match.getHomeTeam().getId()))
                || (match.getAwayTeam() != null && teamId.equals(match.getAwayTeam().getId()));
    }

    private static CalendarRangeFacets facets(FindCalendarRangeQuery query, List<Match> rangeMatches) {
        TreeSet<String> competitions = new TreeSet<>();
        TreeSet<Integer> groups = new TreeSet<>();
        boolean ungrouped = false;
        Map<UUID, CalendarTeamFacet> teams = new LinkedHashMap<>();

        for (Match match : rangeMatches) {
            competitions.add(match.getCompetition());
            if (query.getCompetition() != null && !query.getCompetition().equals(match.getCompetition())) {
                continue;
            }
            if (query.getCompetition() != null) {
                if (match.getGroupNumber() == null) {
                    ungrouped = true;
                } else {
                    groups.add(match.getGroupNumber());
                }
            }
            addTeam(teams, match.getHomeTeam(), match.getCompetition());
            addTeam(teams, match.getAwayTeam(), match.getCompetition());
        }

        List<Integer> groupList = new ArrayList<>(groups);
        if (ungrouped) {
            groupList.add(null);
        }
        List<CalendarTeamFacet> teamList = teams.values().stream()
                .sorted(Comparator.comparing(CalendarTeamFacet::name, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CalendarTeamFacet::teamId))
                .toList();
        return new CalendarRangeFacets(List.copyOf(competitions), groupList, teamList);
    }

    private static void addTeam(Map<UUID, CalendarTeamFacet> teams, Team team, String competition) {
        if (team != null) {
            teams.putIfAbsent(team.getId(), new CalendarTeamFacet(team.getId(), team.getName(), competition));
        }
    }

    private static Comparator<Match> matchOrder() {
        return Comparator.comparing(Match::getDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Match::getCompetition, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Match::getGroupNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Match::getRound)
                .thenComparing(m -> m.getHomeTeam() == null ? null : m.getHomeTeam().getName(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Match::getId);
    }

    private record GroupKey(String competition, Integer groupNumber, String phase) {
    }
}
