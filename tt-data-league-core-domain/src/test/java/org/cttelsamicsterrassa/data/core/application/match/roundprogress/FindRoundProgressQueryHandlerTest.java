package org.cttelsamicsterrassa.data.core.application.match.roundprogress;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressGroupReadModel;
import org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto.RoundProgressReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchCalendarEntry;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FindRoundProgressQueryHandlerTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "TERCERA";
    private static final String PHASE = "1a Fase";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    private MatchRepository matches;
    private MatchOverdueMarkRepository marks;
    private FindRoundProgressQueryHandler handler;
    private MatchCalendarEntry scheduledRound3;
    private MatchCalendarEntry scheduledRound1;

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        marks = mock(MatchOverdueMarkRepository.class);
        Clock clock = Clock.fixed(
                ZonedDateTime.of(TODAY, java.time.LocalTime.NOON, ZoneId.of("Europe/Madrid")).toInstant(),
                ZoneId.of("UTC"));
        handler = new FindRoundProgressQueryHandler(matches, marks, new OverdueGracePeriod(7), clock);

        scheduledRound3 = entry("TERCERA", 2, 3, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 27));
        scheduledRound1 = entry("TERCERA", 2, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 12));
        List<MatchCalendarEntry> entries = List.of(
                scheduledRound1,
                entry("TERCERA", 2, 1, MatchStatus.PLAYED, LocalDate.of(2026, 9, 12)),
                entry("TERCERA", 2, 2, MatchStatus.PLAYED, LocalDate.of(2026, 9, 19)),
                entry("TERCERA", 2, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 26)),
                scheduledRound3,
                entry("PRIMERA", 1, 1, MatchStatus.PLAYED, LocalDate.of(2026, 9, 1)));
        when(matches.findRoundProgress(SOURCE, SEASON)).thenReturn(List.of(
                new RoundProgress(SOURCE, SEASON, "PRIMERA", 1, PHASE, 1, 1, 0, 1),
                new RoundProgress(SOURCE, SEASON, TERCERA, 2, PHASE, 3, 2, 2, 3)));
        when(matches.findCalendarEntries(SOURCE, SEASON)).thenReturn(entries);
        when(marks.findByMatchIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    void returnsEveryStoredJornadaWithItsDerivedCounts() {
        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, null, false));

        assertEquals(TODAY, model.today());
        assertEquals(7, model.overdueGraceDays());
        assertEquals(2, model.groups().size());
        RoundProgressGroupReadModel tercera = model.groups().get(1);
        assertEquals(3, tercera.currentRound());
        assertEquals(2, tercera.lastCompleteRound());
        assertEquals(3, tercera.rounds().size());
        assertEquals(1, tercera.rounds().get(0).postponedMatches());
        assertEquals(1, tercera.rounds().get(2).awaitingResultMatches());
        assertTrue(tercera.rounds().get(2).current());
    }

    @Test
    void theCompetitionFilterKeepsTheGroupHeaders() {
        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, TERCERA, false));

        assertEquals(1, model.groups().size());
        assertEquals(TERCERA, model.competition());
        assertEquals(3, model.groups().getFirst().currentRound());
        assertEquals(2, model.groups().getFirst().lastCompleteRound());
    }

    @Test
    void anUnknownCompetitionIsASuccessWithNoGroups() {
        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, "NOPE", false));

        assertTrue(model.groups().isEmpty());
    }

    @Test
    void onlyOpenDropsClosedJornadasAndEmptyGroupsButKeepsHeaders() {
        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, null, true));

        assertTrue(model.onlyOpen());
        assertEquals(1, model.groups().size(), "the fully played PRIMERA group has no open jornada");
        RoundProgressGroupReadModel tercera = model.groups().getFirst();
        assertEquals(List.of(1, 3), tercera.rounds().stream().map(r -> r.round()).toList(),
                "round 2 is fully played and its window closed");
        assertTrue(tercera.rounds().stream().allMatch(r -> r.open()));
        assertEquals(2, tercera.lastCompleteRound());
    }

    @Test
    void marksAreQueriedOnceWithScheduledIdsOnly() {
        when(marks.findByMatchIds(anyCollection())).thenReturn(
                List.of(new MatchOverdueMark(scheduledRound3.matchId(), ZonedDateTime.now(), "operator")));

        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, TERCERA, false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(marks).findByMatchIds(ids.capture());
        assertEquals(Set.of(scheduledRound1.matchId(), scheduledRound3.matchId()), Set.copyOf(ids.getValue()));
        verifyNoMoreInteractions(marks);
        assertEquals(1, model.groups().getFirst().rounds().get(2).overdueMatches());
        assertEquals(0, model.groups().getFirst().rounds().get(2).awaitingResultMatches());
    }

    @Test
    void neverWritesAnything() {
        handle(new FindRoundProgressQuery(SOURCE, SEASON, null, false));

        verify(matches).findRoundProgress(SOURCE, SEASON);
        verify(matches).findCalendarEntries(SOURCE, SEASON);
        verifyNoMoreInteractions(matches);
    }

    @Test
    void anEmptySourceIsASuccessWithNoGroups() {
        when(matches.findRoundProgress(SOURCE, SEASON)).thenReturn(List.of());
        when(matches.findCalendarEntries(SOURCE, SEASON)).thenReturn(List.of());

        RoundProgressReadModel model = handle(new FindRoundProgressQuery(SOURCE, SEASON, null, false));

        assertTrue(model.groups().isEmpty());
    }

    @Test
    void aRepositoryIllegalArgumentExceptionBecomesAFailedResponse() {
        when(matches.findRoundProgress(any(), any())).thenThrow(new IllegalArgumentException("bad"));

        DomainQueryResponse<RoundProgressReadModel> response =
                handler.handle(new FindRoundProgressQuery(SOURCE, SEASON, null, false));

        assertFalse(response.isSuccess());
    }

    @Test
    void theQueryRejectsMissingSourceOrSeasonAndBlankCompetition() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FindRoundProgressQuery(null, SEASON, null, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FindRoundProgressQuery(SOURCE, null, null, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FindRoundProgressQuery(SOURCE, SEASON, "  ", false));
    }

    private RoundProgressReadModel handle(FindRoundProgressQuery query) {
        DomainQueryResponse<RoundProgressReadModel> response = handler.handle(query);
        assertTrue(response.isSuccess());
        return response.getResponse();
    }

    private static MatchCalendarEntry entry(String competition, int group, int round, MatchStatus status,
                                            LocalDate date) {
        return new MatchCalendarEntry(UUID.randomUUID(), competition, group, PHASE, round, status, date);
    }
}
