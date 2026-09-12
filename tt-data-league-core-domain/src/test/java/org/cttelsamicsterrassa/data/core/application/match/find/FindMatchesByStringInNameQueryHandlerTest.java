package org.cttelsamicsterrassa.data.core.application.match.find;

import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindMatchesByStringInNameQueryHandlerTest {

    @Test
    void rejectsSearchTermsShorterThanTwoCharacters() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);

        var response = new FindMatchesByStringInNameQueryHandler(matchRepository, lineupRepository)
                .handle(new FindMatchesByStringInNameQuery(" a "));

        assertTrue(response.getResponse().isEmpty());
    }

    @Test
    void returnsMatchesFoundBySourceAndSeasonAgnosticFragmentSearchOrderedByMostRecentFirst() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);
        Season season = Season.of(2025);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Anna Club", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Other Club", season, null);
        Match older = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).dateTime(ZonedDateTime.parse("2025-10-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).createExisting();
        Match recent = Match.builder().id(UUID.randomUUID()).source(ImportSource.FCTT).competition("Segona")
                .season(Season.of(2024)).round(1).dateTime(ZonedDateTime.parse("2025-11-01T18:00:00+01:00[Europe/Madrid]"))
                .homeTeam(homeTeam).awayTeam(awayTeam).createExisting();
        when(matchRepository.findAllMatchesByFragmentsInName(List.of("Anna"), 5))
                .thenReturn(List.of(older, recent));
        when(lineupRepository.findAllLineupsByMatchIds(anyList())).thenReturn(List.of());

        List<MatchSearchReadModel> results = new FindMatchesByStringInNameQueryHandler(
                matchRepository, lineupRepository)
                .handle(new FindMatchesByStringInNameQuery("Anna")).getResponse();

        assertEquals(2, results.size());
        assertEquals(recent.getId(), results.get(0).id());
        assertEquals(older.getId(), results.get(1).id());
    }
}
