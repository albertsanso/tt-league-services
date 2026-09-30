package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkMatchOverdueCommandHandlerTest {

    private static final Season SEASON = Season.of(2026);
    private static final ZonedDateTime NOW = ZonedDateTime.of(
            LocalDate.of(2026, 9, 8), LocalTime.of(10, 15), Match.COMPETITION_ZONE);

    @Test
    void anUnknownMatchFails() {
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(any())).thenReturn(Optional.empty());
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches,
                mock(MatchOverdueMarkRepository.class), Clock.systemUTC());

        UUID id = UUID.randomUUID();
        DomainCommandResponse response = handler.handle(new MarkMatchOverdueCommand(id, "admin"));

        assertFalse(response.isSuccess());
        assertEquals("Match not found: " + id, String.valueOf(response.getResponse()));
    }

    @Test
    void aPlayedMatchIsRejected() {
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(any())).thenReturn(Optional.of(played()));
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches,
                mock(MatchOverdueMarkRepository.class), Clock.systemUTC());

        DomainCommandResponse response = handler.handle(new MarkMatchOverdueCommand(UUID.randomUUID(), "admin"));

        assertFalse(response.isSuccess());
        assertEquals("Only scheduled matches can be marked overdue", String.valueOf(response.getResponse()));
    }

    @Test
    void aMatchCannotBeMarkedBeforeTheDayAfterItsDate() {
        Match onTheMarkDay = scheduledOn(NOW.withHour(20));
        Match undated = scheduledOn(null);
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(onTheMarkDay.getId())).thenReturn(Optional.of(onTheMarkDay));
        when(matches.findMatchById(undated.getId())).thenReturn(Optional.of(undated));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches, marks,
                Clock.fixed(NOW.toInstant(), Match.COMPETITION_ZONE));

        for (Match match : java.util.List.of(onTheMarkDay, undated)) {
            DomainCommandResponse response = handler.handle(new MarkMatchOverdueCommand(match.getId(), "admin"));

            assertFalse(response.isSuccess());
            assertEquals("A match can only be marked overdue from the day after its scheduled date",
                    String.valueOf(response.getResponse()));
        }
        verify(marks, never()).save(any());
    }

    @Test
    void aMatchCanBeMarkedOnTheDayAfterItsDate() {
        Match yesterday = scheduledOn(NOW.minusDays(1).withHour(21));
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(yesterday.getId())).thenReturn(Optional.of(yesterday));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchId(yesterday.getId())).thenReturn(Optional.empty());
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches, marks,
                Clock.fixed(NOW.toInstant(), Match.COMPETITION_ZONE));

        assertTrue(handler.handle(new MarkMatchOverdueCommand(yesterday.getId(), "admin")).isSuccess());
    }

    @Test
    void marksAScheduledMatchWithTheClockInstantInTheCompetitionZone() {
        Match scheduled = scheduled();
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(scheduled.getId())).thenReturn(Optional.of(scheduled));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.findByMatchId(scheduled.getId())).thenReturn(Optional.empty());
        Clock clock = Clock.fixed(NOW.toInstant(), Match.COMPETITION_ZONE);
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches, marks, clock);

        DomainCommandResponse response = handler.handle(new MarkMatchOverdueCommand(scheduled.getId(), "admin"));

        assertTrue(response.isSuccess());
        MatchOverdueMark mark = (MatchOverdueMark) response.getResponse();
        assertEquals("admin", mark.markedBy());
        assertEquals(NOW, mark.markedAt());
        verify(marks).save(mark);
    }

    @Test
    void reMarkingIsIdempotentAndKeepsTheOriginalAuthorAndTime() {
        Match scheduled = scheduled();
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(scheduled.getId())).thenReturn(Optional.of(scheduled));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        MatchOverdueMark existing = new MatchOverdueMark(scheduled.getId(),
                ZonedDateTime.of(2026, 9, 1, 9, 0, 0, 0, Match.COMPETITION_ZONE), "first-author");
        when(marks.findByMatchId(scheduled.getId())).thenReturn(Optional.of(existing));
        MarkMatchOverdueCommandHandler handler = new MarkMatchOverdueCommandHandler(matches, marks,
                Clock.systemUTC());

        DomainCommandResponse response = handler.handle(new MarkMatchOverdueCommand(scheduled.getId(), "later"));

        assertTrue(response.isSuccess());
        assertEquals(existing, response.getResponse());
        verify(marks, never()).save(any());
    }

    private Match played() {
        Team home = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Home", SEASON, null);
        Team away = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Away", SEASON, null);
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).competition("liga").season(SEASON)
                .round(1).homeTeam(home).awayTeam(away).winnerTeam(home).homeGamesWon(3).awayGamesWon(1)
                .status(MatchStatus.PLAYED).createExisting();
    }

    private Match scheduled() {
        return scheduledOn(ZonedDateTime.of(LocalDate.of(2026, 9, 6), LocalTime.of(18, 0), Match.COMPETITION_ZONE));
    }

    private Match scheduledOn(ZonedDateTime dateTime) {
        Team home = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Home", SEASON, null);
        Team away = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Away", SEASON, null);
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).competition("liga").season(SEASON)
                .round(2).dateTime(dateTime).homeTeam(home).awayTeam(away).status(MatchStatus.SCHEDULED)
                .createExisting();
    }
}