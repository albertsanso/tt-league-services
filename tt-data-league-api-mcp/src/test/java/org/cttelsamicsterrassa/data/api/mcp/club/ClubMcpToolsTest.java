package org.cttelsamicsterrassa.data.api.mcp.club;

import org.albertsanso.commons.query.DomainQueryResponse;
import org.albertsanso.commons.query.QueryBus;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubMcpToolsTest {

    private static final UUID CLUB_ID = UUID.randomUUID();

    @Test
    void findClubByIdMapsFederatedClub() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubMcpTools tools = toolsWith(queryBus);
        FederatedClub club = FederatedClub.createExisting(CLUB_ID, ImportSource.FCTT, "Club A");
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(club));

        ClubDto result = tools.findClubById(CLUB_ID);

        assertEquals(new ClubDto(CLUB_ID, "Club A", "FCTT"), result);
    }

    @Test
    void findClubByIdThrowsWhenQueryFails() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubMcpTools tools = toolsWith(queryBus);
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.failResponse(null));

        assertThrows(IllegalStateException.class, () -> tools.findClubById(CLUB_ID));
    }

    @Test
    void findClubsByStringInNameRejectsShortSearchTerms() {
        ClubMcpTools tools = toolsWith(mock(QueryBus.class));

        assertThrows(IllegalArgumentException.class, () -> tools.findClubsByStringInName(" a ", null));
    }

    @Test
    void findClubsByStringInNameMapsSearchReadModels() {
        QueryBus queryBus = mock(QueryBus.class);
        ClubMcpTools tools = toolsWith(queryBus);
        UUID federatedId = UUID.randomUUID();
        when(queryBus.push(any())).thenReturn(DomainQueryResponse.sucessResponse(List.of(
                new ClubSearchReadModel(
                        CLUB_ID,
                        "Club A",
                        List.of(new ClubFederatedReadModel(federatedId, "Club A RFETM", ImportSource.RFETM))))));

        List<ClubDto> results = tools.findClubsByStringInName("Club A", null);

        assertEquals(CLUB_ID, results.getFirst().id());
        assertEquals(List.of("RFETM"), results.getFirst().sources());
    }

    private static ClubMcpTools toolsWith(QueryBus queryBus) {
        ClubMcpTools tools = new ClubMcpTools();
        ReflectionTestUtils.setField(tools, "queryBus", queryBus);
        return tools;
    }
}
