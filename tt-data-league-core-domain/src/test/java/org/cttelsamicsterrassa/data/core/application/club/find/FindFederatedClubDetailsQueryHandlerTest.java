package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindFederatedClubDetailsQueryHandlerTest {

    @Test
    void excludesScheduledMatchesFromCompetitionSummaries() {
        // FEAT-00079: the repository deliberately returns mixed statuses (consolidation scope);
        // the handler must aggregate over PLAYED only.
        Season season = Season.of(2025);
        FederatedClub club = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa");
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, club);
        Team rival = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival", season, null);

        Match played = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(1).homeTeam(homeTeam).awayTeam(rival).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        Match scheduled = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).round(2).homeTeam(homeTeam).awayTeam(rival)
                .status(MatchStatus.SCHEDULED).createExisting();

        FederatedClubRepository clubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);

        when(clubRepository.findFederatedClubById(club.getId())).thenReturn(Optional.of(club));
        when(teamRepository.findAllTeamsByFederatedClubId(club.getId())).thenReturn(List.of(homeTeam));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of());
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(played, scheduled));
        when(playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(any(), any()))
                .thenReturn(Map.of());

        FederatedClubDetailsReadModel details = new FindFederatedClubDetailsQueryHandler(
                clubRepository, teamRepository, matchRepository, playerSeasonRepository)
                .handle(new FindFederatedClubDetailsQuery(club.getId())).getResponse();

        assertEquals(1, details.competitions().size());
        assertEquals(1, details.competitions().getFirst().matchCount());
        assertEquals(1, details.competitions().getFirst().wins());
        assertEquals(0, details.competitions().getFirst().draws());
        assertEquals(0, details.competitions().getFirst().losses());
    }
}
