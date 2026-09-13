package org.cttelsamicsterrassa.data.api.mcp.match;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchPage;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchMcpToolsTest {

    @Test
    void searchRejectsInvalidSource() {
        MatchMcpTools tools = toolsWith(mock(QueryBus.class));

        assertThrows(IllegalArgumentException.class, () -> tools.search(
                "NOT_A_SOURCE", "2024-2025", null, null, null, null, null, null, null, null, null));
    }

    @Test
    void searchMapsMatchSearchPage() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchMcpTools tools = toolsWith(queryBus);
        UUID matchId = UUID.randomUUID();
        MatchSearchReadModel match = new MatchSearchReadModel(
                matchId, ImportSource.RFETM, "1a Div", Season.fromFormatted("2024-2025"), 1, "REGULAR", null,
                "Home Team", "Away Team", "Home Team", 5, 2, 27, 15, false, List.of(), List.of());
        when(queryBus.push(any())).thenReturn(
                DomainQueryResponse.sucessResponse(new MatchSearchPage(List.of(match), 1, 0, 10, false)));

        MatchMcpTools.SearchResult result = tools.search(
                "rfetm", "2024-2025", null, null, null, null, null, null, null, null, null);

        assertEquals(1, result.total());
        assertEquals(matchId, result.matches().getFirst().id());
    }

    @Test
    void detailsThrowsWhenMatchNotFound() {
        QueryBus queryBus = mock(QueryBus.class);
        MatchMcpTools tools = toolsWith(queryBus);
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        UUID id = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> tools.details(id));
    }

    private static MatchMcpTools toolsWith(QueryBus queryBus) {
        MatchMcpTools tools = new MatchMcpTools();
        ReflectionTestUtils.setField(tools, "queryBus", queryBus);
        return tools;
    }
}
