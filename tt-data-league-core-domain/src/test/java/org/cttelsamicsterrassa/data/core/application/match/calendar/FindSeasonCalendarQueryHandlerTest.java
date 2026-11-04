package org.cttelsamicsterrassa.data.core.application.match.calendar;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.CalendarRoundReadModel;
import org.cttelsamicsterrassa.data.core.application.match.calendar.dto.SeasonCalendarReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.CalendarMatchState;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindSeasonCalendarQueryHandlerTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera-nacional-masculino";
    private static final LocalDate JORNADA_1 = LocalDate.of(2026, 9, 5);
    private static final LocalDate JORNADA_2 = LocalDate.of(2026, 9, 19);

    @Test
    void aPendingJornadaThreeDaysAfterItsDateIsAwaitingResultAndNotOverdue() {
        List<Match> matches = g1Shape();
        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(3)));
        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        SeasonCalendarReadModel model = response.getResponse();
        assertEquals(1, model.groups().size());
        CalendarGroupReadModel group = model.groups().getFirst();
        assertEquals(1, group.currentRound());
        assertNull(group.lastCompleteRound(), "jornada 1 still holds pending actas");
        assertEquals(9, group.scheduledMatches());
        assertEquals(3, group.playedMatches());
        assertEquals(0, group.overdueMatches());

        CalendarRoundReadModel round1 = group.rounds().getFirst();
        assertEquals(CalendarMatchState.AWAITING_RESULT,
                round1.matches().stream().filter(m -> m.status() == MatchStatus.SCHEDULED)
                        .findFirst().orElseThrow().calendarState());
    }

    @Test
    void theSameShapeTenDaysAfterItsDateIsOverdueAndCounted() {
        List<Match> matches = g1Shape();
        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(10)));
        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        CalendarGroupReadModel group = response.getResponse().groups().getFirst();
        assertEquals(3, group.overdueMatches());
        CalendarRoundReadModel round1 = group.rounds().stream()
                .filter(r -> r.round() == 1).findFirst().orElseThrow();
        assertTrue(round1.matches().stream().filter(m -> m.status() == MatchStatus.SCHEDULED)
                .allMatch(m -> m.calendarState() == CalendarMatchState.OVERDUE));
        CalendarRoundReadModel round2 = group.rounds().stream()
                .filter(r -> r.round() == 2).findFirst().orElseThrow();
        assertTrue(round2.matches().stream()
                .allMatch(m -> m.calendarState() == CalendarMatchState.UPCOMING));
    }

    @Test
    void aManualMarkWinsOverDerivationAndIsCounted() {
        List<Match> matches = g1Shape();
        Match scheduledJornada2 = matches.stream()
                .filter(m -> m.getRound() == 2).findFirst().orElseThrow();
        MatchOverdueMark mark = new MatchOverdueMark(scheduledJornada2.getId(),
                ZonedDateTime.now(), "admin");

        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of(mark));

        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(3)));
        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        CalendarGroupReadModel group = response.getResponse().groups().getFirst();
        assertEquals(1, group.overdueMatches());
        var markedModel = group.rounds().get(1).matches().stream()
                .filter(m -> m.id().equals(scheduledJornada2.getId())).findFirst().orElseThrow();
        assertEquals(CalendarMatchState.OVERDUE, markedModel.calendarState());
        assertTrue(markedModel.overdueMarked());
        assertEquals("admin", markedModel.overdueMarkedBy());
        assertEquals(mark.markedAt(), markedModel.overdueMarkedAt());
    }

    @Test
    void aRoundBelowTheCurrentRoundIsPostponedAndCounted() {
        Match postponed = scheduled(1, JORNADA_1, 4);
        List<Match> round2Played = List.of(
                played(2, JORNADA_2, 4), played(2, JORNADA_2, 5),
                played(2, JORNADA_2, 6), played(2, JORNADA_2, 7),
                played(2, JORNADA_2, 8), played(2, JORNADA_2, 9));
        List<Match> matches = new java.util.ArrayList<>(round2Played);
        matches.add(postponed);

        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(3)));
        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        CalendarGroupReadModel group = response.getResponse().groups().getFirst();
        assertEquals(2, group.currentRound());
        assertEquals(1, group.postponedMatches());
        var postponedModel = group.rounds().stream()
                .filter(r -> r.round() == 1).findFirst().orElseThrow().matches().getFirst();
        assertEquals(CalendarMatchState.POSTPONED, postponedModel.calendarState());
    }

    @Test
    void aRoundFilterNarrowsTheRoundsButNotTheGroupHeader() {
        List<Match> matches = g1Shape();
        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(3)));
        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(roundQuery(2));

        CalendarGroupReadModel group = response.getResponse().groups().getFirst();
        assertEquals(9, group.scheduledMatches(), "the header still describes the whole group");
        assertEquals(1, group.rounds().size());
        assertEquals(2, group.rounds().getFirst().round());
    }

    @Test
    void onlyScheduledIdsAreLookedUpAsMarks() {
        List<Match> matches = g1Shape();
        MatchRepository matchRepository = matchRepositoryReturning(matches);
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());

        List<UUID> scheduledIds = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED).map(Match::getId).toList();
        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1.plusDays(3)));
        handler.handle(query());

        verify(marks).findByMatchIds(scheduledIds);
    }

    @Test
    void anEmptyCompetitionYieldsEmptyGroups() {
        MatchRepository matchRepository = matchRepositoryReturning(List.of());
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        FindSeasonCalendarQueryHandler handler = handler(matchRepository, marks, today(JORNADA_1));

        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        assertTrue(response.isSuccess());
        assertTrue(response.getResponse().groups().isEmpty());
    }

    @Test
    void aRepositoryFailureIsReportedAsAFailedResponse() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        when(matchRepository.findMatchesBySourceSeasonAndCompetition(SOURCE, SEASON, TERCERA))
                .thenThrow(new IllegalArgumentException("boom"));
        FindSeasonCalendarQueryHandler handler = handler(matchRepository,
                mock(MatchOverdueMarkRepository.class), today(JORNADA_1));

        DomainQueryResponse<SeasonCalendarReadModel> response = handler.handle(query());

        assertFalse(response.isSuccess());
    }

    private FindSeasonCalendarQuery query() {
        return new FindSeasonCalendarQuery(SOURCE, SEASON, TERCERA, null, null);
    }

    private FindSeasonCalendarQuery roundQuery(int round) {
        return new FindSeasonCalendarQuery(SOURCE, SEASON, TERCERA, null, round);
    }

    private FindSeasonCalendarQueryHandler handler(MatchRepository matches, MatchOverdueMarkRepository marks,
                                                   Clock clock) {
        return new FindSeasonCalendarQueryHandler(matches, marks, new OverdueGracePeriod(7), clock);
    }

    private MatchRepository matchRepositoryReturning(List<Match> matches) {
        MatchRepository repository = mock(MatchRepository.class);
        when(repository.findMatchesBySourceSeasonAndCompetition(SOURCE, SEASON, TERCERA)).thenReturn(matches);
        return repository;
    }

    private Clock today(LocalDate date) {
        return Clock.fixed(ZonedDateTime.of(date, LocalTime.NOON, Match.COMPETITION_ZONE).toInstant(),
                Match.COMPETITION_ZONE);
    }

    private List<Match> g1Shape() {
        return List.of(
                played(1, JORNADA_1, 0), played(1, JORNADA_1, 1), played(1, JORNADA_1, 2),
                scheduled(1, JORNADA_1, 3), scheduled(1, JORNADA_1, 4), scheduled(1, JORNADA_1, 5),
                scheduled(2, JORNADA_2, 6), scheduled(2, JORNADA_2, 7), scheduled(2, JORNADA_2, 8),
                scheduled(2, JORNADA_2, 9), scheduled(2, JORNADA_2, 10), scheduled(2, JORNADA_2, 11));
    }

    private Team team(int index) {
        return Team.createExisting(UUID.randomUUID(), SOURCE, "Team " + index, SEASON, null);
    }

    private Match played(int round, LocalDate date, int index) {
        Team home = team(index);
        Team away = team(index + 100);
        return Match.builder().id(UUID.randomUUID()).source(SOURCE).competition(TERCERA).season(SEASON)
                .groupNumber(1).round(round).phase("1a Fase").dateTime(at(date))
                .homeTeam(home).awayTeam(away).winnerTeam(home)
                .homeGamesWon(4).awayGamesWon(1).status(MatchStatus.PLAYED).createExisting();
    }

    private Match scheduled(int round, LocalDate date, int index) {
        return Match.builder().id(UUID.randomUUID()).source(SOURCE).competition(TERCERA).season(SEASON)
                .groupNumber(1).round(round).phase("1a Fase").dateTime(at(date))
                .homeTeam(team(index)).awayTeam(team(index + 100)).status(MatchStatus.SCHEDULED).createExisting();
    }

    private ZonedDateTime at(LocalDate date) {
        return ZonedDateTime.of(date, LocalTime.of(18, 30), Match.COMPETITION_ZONE);
    }
}