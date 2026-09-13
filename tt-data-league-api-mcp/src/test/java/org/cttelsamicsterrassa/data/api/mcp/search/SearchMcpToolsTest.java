package org.cttelsamicsterrassa.data.api.mcp.search;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchMcpToolsTest {

    @Test
    void rejectsShortSearchTerms() {
        SearchMcpTools tools = toolsWith(mock(QueryBus.class));

        assertThrows(IllegalArgumentException.class, () -> tools.search(" a "));
    }

    @Test
    void groupsResultsByEntityType() {
        QueryBus queryBus = mock(QueryBus.class);
        SearchMcpTools tools = toolsWith(queryBus);
        UUID clubId = UUID.randomUUID();
        when(queryBus.push(any()))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of()))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of(
                        new ClubSearchReadModel(clubId, "Club A", List.of()))))
                .thenReturn(DomainQueryResponse.sucessResponse(List.of()));

        SearchMcpTools.GlobalSearchResult result = tools.search("Club A");

        assertEquals(0, result.players().size());
        assertEquals(1, result.clubs().size());
        assertEquals(clubId, result.clubs().getFirst().id());
        assertEquals(0, result.matches().size());
    }

    private static SearchMcpTools toolsWith(QueryBus queryBus) {
        SearchMcpTools tools = new SearchMcpTools();
        ReflectionTestUtils.setField(tools, "queryBus", queryBus);
        return tools;
    }
}
