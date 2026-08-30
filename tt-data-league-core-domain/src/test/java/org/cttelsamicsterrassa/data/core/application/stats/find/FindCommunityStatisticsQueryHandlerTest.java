package org.cttelsamicsterrassa.data.core.application.stats.find;

import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.SeasonAvailability;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FindCommunityStatisticsQueryHandlerTest {

    @Test
    void computesTotalsAndCurrentSeasonCountsFromTheMostRecentSeason() {
        FederatedPlayerRepository players = mock(FederatedPlayerRepository.class);
        FederatedClubRepository clubs = mock(FederatedClubRepository.class);
        MatchRepository matches = mock(MatchRepository.class);

        when(players.countUniquePlayerNames()).thenReturn(1248L);
        when(clubs.countUniqueClubNames()).thenReturn(186L);
        when(matches.countAllMatches()).thenReturn(8432L);
        when(matches.findAllSeasons()).thenReturn(List.of("2025-2026", "2024-2025"));

        CommunityStatisticsReadModel result = new FindCommunityStatisticsQueryHandler(
                players, clubs, matches)
                .handle(new FindCommunityStatisticsQuery())
                .getResponse();

        assertEquals(1248L, result.players().total());
        assertEquals(186L, result.clubs().total());
        assertEquals(8432L, result.matches().total());
        assertEquals("2025-2026", result.season().name());
        assertEquals(SeasonAvailability.IN_PROGRESS, result.season().status());
    }

    @Test
    void returnsZeroScopedCountsAndUnavailableSeasonWhenNoMatchSeasonExists() {
        FederatedPlayerRepository players = mock(FederatedPlayerRepository.class);
        FederatedClubRepository clubs = mock(FederatedClubRepository.class);
        MatchRepository matches = mock(MatchRepository.class);

        when(players.countUniquePlayerNames()).thenReturn(0L);
        when(clubs.countUniqueClubNames()).thenReturn(0L);
        when(matches.countAllMatches()).thenReturn(0L);
        when(matches.findAllSeasons()).thenReturn(List.of());

        CommunityStatisticsReadModel result = new FindCommunityStatisticsQueryHandler(
                players, clubs, matches)
                .handle(new FindCommunityStatisticsQuery())
                .getResponse();

        assertEquals(0L, result.players().total());
        assertFalse(result.season().name() != null && !result.season().name().isBlank());
        assertEquals(SeasonAvailability.UNAVAILABLE, result.season().status());
    }

    @Test
    void propagatesRepositoryFailures() {
        FederatedPlayerRepository players = mock(FederatedPlayerRepository.class);
        FederatedClubRepository clubs = mock(FederatedClubRepository.class);
        MatchRepository matches = mock(MatchRepository.class);

        when(players.countUniquePlayerNames()).thenThrow(new IllegalStateException("query failed"));

        assertThrows(IllegalStateException.class,
                () -> new FindCommunityStatisticsQueryHandler(players, clubs, matches)
                        .handle(new FindCommunityStatisticsQuery()));
    }
}
