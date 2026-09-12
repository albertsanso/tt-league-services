package org.cttelsamicsterrassa.data.core.application.club.find;

import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindClubDetailsQueryHandlerTest {

    @Test
    void computesPerPlayerWinDrawAndLossTotalsFromLineups() {
        UUID clubId = UUID.randomUUID();
        Club club = Club.createExisting(clubId, "Club Terrassa");
        Season season = Season.of(2025);
        FederatedClub federatedClub = FederatedClub.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa", club);
        Team homeTeam = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Club Terrassa 1", season, federatedClub);
        Team rivalA = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival A", season, null);
        Team rivalB = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival B", season, null);
        Team rivalC = Team.createExisting(UUID.randomUUID(), ImportSource.RFETM, "Rival C", season, null);

        PlayerSeason anna = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Anna Player", "123", null, season);
        PlayerSeason marc = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "Marc Player", "456", null, season);

        Match wonMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalA).homeGamesWon(5).awayGamesWon(2)
                .winnerTeam(homeTeam).createExisting();
        Match lostMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(rivalB).awayTeam(homeTeam).homeGamesWon(5).awayGamesWon(1)
                .winnerTeam(rivalB).createExisting();
        Match drawnMatch = Match.builder().id(UUID.randomUUID()).source(ImportSource.RFETM).competition("Preferent")
                .season(season).homeTeam(homeTeam).awayTeam(rivalC).homeGamesWon(4).awayGamesWon(4)
                .createExisting();

        Lineup annaWon = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(wonMatch).team(homeTeam).player(anna).createExisting();
        Lineup annaLost = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(lostMatch).team(homeTeam).player(anna).createExisting();
        Lineup annaDrew = Lineup.builder().id(UUID.randomUUID()).source(ImportSource.RFETM)
                .match(drawnMatch).team(homeTeam).player(anna).createExisting();

        ClubRepository clubRepository = mock(ClubRepository.class);
        FederatedClubRepository federatedClubRepository = mock(FederatedClubRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerSeasonRepository playerSeasonRepository = mock(PlayerSeasonRepository.class);
        LineupRepository lineupRepository = mock(LineupRepository.class);

        when(clubRepository.findClubById(clubId)).thenReturn(Optional.of(club));
        when(federatedClubRepository.findAllFederatedClubsByClubId(clubId)).thenReturn(List.of(federatedClub));
        when(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId())).thenReturn(List.of(homeTeam));
        when(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(anna, marc));
        when(matchRepository.findAllMatchesByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(List.of(wonMatch, lostMatch, drawnMatch));
        when(playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(any(), eq(ImportSource.RFETM)))
                .thenReturn(Map.of());
        when(lineupRepository.findAllLineupsByPlayerSeasonIds(anyList()))
                .thenReturn(List.of(annaWon, annaLost, annaDrew));

        ClubDetailsReadModel details = new FindClubDetailsQueryHandler(
                clubRepository, federatedClubRepository, teamRepository, matchRepository,
                playerSeasonRepository, lineupRepository)
                .handle(new FindClubDetailsQuery(clubId)).getResponse();

        FederatedClubPlayerReadModel annaModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(anna.getId())).findFirst().orElseThrow();
        assertEquals(3, annaModel.matchCount());
        assertEquals(1, annaModel.wins());
        assertEquals(1, annaModel.draws());
        assertEquals(1, annaModel.losses());

        FederatedClubPlayerReadModel marcModel = details.players().stream()
                .filter(player -> player.playerSeasonId().equals(marc.getId())).findFirst().orElseThrow();
        assertEquals(0, marcModel.matchCount());
        assertEquals(0, marcModel.wins());
        assertEquals(0, marcModel.draws());
        assertEquals(0, marcModel.losses());
    }
}
