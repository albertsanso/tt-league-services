package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubCompetitionDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubMatchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindFederatedClubCompetitionDetailsQueryHandlerTest {

    @Test
    void showsATieAsADrawInATieEligibleCompetitionButExcludesItEntirelyElsewhere() {
        // FEAT-00066: match-record lists must show a draw only for a tie-eligible competition,
        // and must not list a tie at all for any other competition.
        Season season = Season.of(2025);
        FederatedClub club = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa");
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, club);
        Team rival = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival", season, null);

        Match decidedMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .competition("preferent").season(season).round(1)
                .homeTeam(homeTeam).awayTeam(rival).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        Match ineligibleTie = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .competition("preferent").season(season).round(2)
                .homeTeam(homeTeam).awayTeam(rival).homeGamesWon(3).awayGamesWon(3)
                .createExisting();

        FederatedClubRepository clubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        when(clubRepository.findFederatedClubById(club.getId())).thenReturn(Optional.of(club));
        when(teamRepository.findAllTeamsByFederatedClubId(club.getId())).thenReturn(List.of(homeTeam));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "preferent"))
                .thenReturn(List.of(decidedMatch, ineligibleTie));

        FederatedClubCompetitionDetailsReadModel details = new FindFederatedClubCompetitionDetailsQueryHandler(
                clubRepository, teamRepository, matchRepository)
                .handle(new FindFederatedClubCompetitionDetailsQuery(club.getId(), season, "preferent"))
                .getResponse();

        assertEquals(1, details.matches().size());
        FederatedClubMatchReadModel visible = details.matches().getFirst();
        assertEquals(decidedMatch.getId(), visible.id());
        assertEquals("win", visible.result());
    }

    @Test
    void showsATieAsADrawWhenTheCompetitionIsTieEligible() {
        Season season = Season.of(2025);
        FederatedClub club = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa");
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, club);
        Team rival = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival", season, null);

        Match eligibleTie = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .competition("super-divisio-masculino").season(season).round(1)
                .homeTeam(homeTeam).awayTeam(rival).homeGamesWon(3).awayGamesWon(3)
                .createExisting();

        FederatedClubRepository clubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        when(clubRepository.findFederatedClubById(club.getId())).thenReturn(Optional.of(club));
        when(teamRepository.findAllTeamsByFederatedClubId(club.getId())).thenReturn(List.of(homeTeam));
        when(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                List.of(homeTeam.getId()), ImportSource.RFETM, season, "super-divisio-masculino"))
                .thenReturn(List.of(eligibleTie));

        FederatedClubCompetitionDetailsReadModel details = new FindFederatedClubCompetitionDetailsQueryHandler(
                clubRepository, teamRepository, matchRepository)
                .handle(new FindFederatedClubCompetitionDetailsQuery(club.getId(), season, "super-divisio-masculino"))
                .getResponse();

        assertEquals(1, details.matches().size());
        assertEquals("draw", details.matches().getFirst().result());
    }
}
