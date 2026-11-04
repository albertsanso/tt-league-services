package org.cttelsamicsterrassa.data.core.application.match.calendar.mark;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClearMatchOverdueMarkCommandHandlerTest {

    private static final Season SEASON = Season.of(2026);

    @Test
    void anUnknownMatchFails() {
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(any())).thenReturn(Optional.empty());
        ClearMatchOverdueMarkCommandHandler handler = new ClearMatchOverdueMarkCommandHandler(matches,
                mock(MatchOverdueMarkRepository.class));

        DomainCommandResponse response = handler.handle(new ClearMatchOverdueMarkCommand(UUID.randomUUID()));

        assertFalse(response.isSuccess());
    }

    @Test
    void clearingIsIdempotent() {
        Match match = played();
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(match.getId())).thenReturn(Optional.of(match));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.deleteByMatchId(match.getId())).thenReturn(false);
        ClearMatchOverdueMarkCommandHandler handler = new ClearMatchOverdueMarkCommandHandler(matches, marks);

        DomainCommandResponse response = handler.handle(new ClearMatchOverdueMarkCommand(match.getId()));

        assertTrue(response.isSuccess());
        assertEquals(false, response.getResponse());
    }

    @Test
    void clearingIsAllowedOnAPlayedMatch() {
        Match match = played();
        MatchRepository matches = mock(MatchRepository.class);
        when(matches.findMatchById(match.getId())).thenReturn(Optional.of(match));
        MatchOverdueMarkRepository marks = mock(MatchOverdueMarkRepository.class);
        when(marks.deleteByMatchId(match.getId())).thenReturn(true);
        ClearMatchOverdueMarkCommandHandler handler = new ClearMatchOverdueMarkCommandHandler(matches, marks);

        DomainCommandResponse response = handler.handle(new ClearMatchOverdueMarkCommand(match.getId()));

        assertTrue(response.isSuccess());
        assertEquals(true, response.getResponse());
    }

    private Match played() {
        Team home = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Home", SEASON, null);
        Team away = Team.createExisting(UUID.randomUUID(), ImportSource.FCTT, "Away", SEASON, null);
        return Match.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).competition("liga").season(SEASON)
                .round(1).homeTeam(home).awayTeam(away).winnerTeam(home).homeGamesWon(3).awayGamesWon(1)
                .status(MatchStatus.PLAYED).createExisting();
    }
}