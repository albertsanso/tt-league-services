package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindClubsByStringInNameQueryHandlerTest {

    @Test
    void excludesScheduledMatchesFromSearchResultSummariesAndSeasons() {
        // FEAT-00079: club-search stats and season lists must not count SCHEDULED fixtures.
        Season season = Season.of(2025);
        UUID clubId = UUID.randomUUID();
        Club club = Club.createExisting(clubId, "Club Terrassa");
        FederatedClub federatedClub = FederatedClub.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", club);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, federatedClub);
        Team rival = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival", season, null);

        Match played = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).homeTeam(homeTeam).awayTeam(rival).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        Match scheduled = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(2).homeTeam(homeTeam).awayTeam(rival)
                .status(MatchStatus.SCHEDULED).createExisting();

        ClubRepository clubRepository = mock(ClubRepository.class);
        FederatedClubRepository federatedClubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);

        when(federatedClubRepository.findAllFederatedClubsByFragmentsInName(anyList()))
                .thenReturn(List.of(federatedClub));
        when(clubRepository.findAllClubs()).thenReturn(List.of(club));
        when(federatedClubRepository.findAllFederatedClubsByClubId(clubId)).thenReturn(List.of(federatedClub));
        when(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId())).thenReturn(List.of(homeTeam));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(played, scheduled));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), any())).thenReturn(List.of());

        List<ClubSearchReadModel> results = new FindClubsByStringInNameQueryHandler(
                clubRepository, federatedClubRepository, teamRepository, matchRepository, playerSeasonRepository)
                .handle(new FindClubsByStringInNameQuery("club terrassa")).getResponse();

        assertEquals(1, results.size());
        ClubSearchReadModel model = results.getFirst();
        assertEquals(1, model.competitions().size());
        assertEquals(1, model.competitions().getFirst().matchCount());
        assertEquals(1, model.competitions().getFirst().wins());
        assertEquals(List.of(season), model.seasons());
    }
}
