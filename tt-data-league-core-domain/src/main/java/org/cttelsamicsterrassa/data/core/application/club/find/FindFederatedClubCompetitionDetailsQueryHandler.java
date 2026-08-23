package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Named
public class FindFederatedClubCompetitionDetailsQueryHandler
        extends DomainQueryHandler<FindFederatedClubCompetitionDetailsQuery, FederatedClubCompetitionDetailsReadModel> {

    private final FederatedClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    @Inject
    public FindFederatedClubCompetitionDetailsQueryHandler(
            FederatedClubRepository clubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository) {
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public DomainQueryResponse<FederatedClubCompetitionDetailsReadModel> handle(FindFederatedClubCompetitionDetailsQuery query) {
        if (query.getFederatedClubId() == null
                || query.getSeason() == null
                || query.getCompetition() == null
                || query.getCompetition().isBlank()) {
            return DomainQueryResponse.failResponse(null);
        }

        return clubRepository.findFederatedClubById(query.getFederatedClubId())
                .flatMap(club -> findDetails(club, query))
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private java.util.Optional<FederatedClubCompetitionDetailsReadModel> findDetails(
            FederatedClub club,
            FindFederatedClubCompetitionDetailsQuery query) {
        List<UUID> teamIds = teamRepository.findAllTeamsByFederatedClubId(club.getId()).stream()
                .filter(team -> club.getSource().equals(team.getSource()))
                .filter(team -> query.getSeason().equals(team.getSeason()))
                .map(Team::getId)
                .toList();
        if (teamIds.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<Match> matches = matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                teamIds, club.getSource(), query.getSeason(), query.getCompetition().trim());
        if (matches.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new FederatedClubCompetitionDetailsReadModel(
                club.getId(),
                club.getName(),
                club.getSource(),
                query.getCompetition().trim(),
                query.getSeason(),
                matches.stream()
                        .sorted(Comparator.comparing(Match::getRound).thenComparing(Match::getId))
                        .map(match -> toReadModel(match, teamIds))
                        .toList()));
    }

    private FederatedClubMatchReadModel toReadModel(Match match, List<UUID> clubTeamIds) {
        UUID winnerId = match.getWinnerTeam() == null ? null : match.getWinnerTeam().getId();
        String result = winnerId == null
                ? "draw"
                : clubTeamIds.contains(winnerId) ? "win" : "loss";
        return new FederatedClubMatchReadModel(
                match.getId(),
                match.getHomeTeam().getName(),
                match.getAwayTeam().getName(),
                match.getHomeGamesWon(),
                match.getAwayGamesWon(),
                result,
                match.getRound(),
                match.getDateTime(),
                match.getCity(),
                match.getVenue());
    }
}
