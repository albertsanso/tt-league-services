package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Named
public class FindFederatedClubCompetitionDetailsQueryHandler
        extends DomainQueryHandler<FindFederatedClubCompetitionDetailsQuery, FederatedClubCompetitionDetailsReadModel> {

    private final FederatedClubRepository clubRepository;
    private final ClubRepository canonicalClubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    public FindFederatedClubCompetitionDetailsQueryHandler(
            FederatedClubRepository clubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository) {
        this(clubRepository, null, teamRepository, matchRepository);
    }

    @Inject
    public FindFederatedClubCompetitionDetailsQueryHandler(
            FederatedClubRepository clubRepository,
            ClubRepository canonicalClubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository) {
        this.clubRepository = clubRepository;
        this.canonicalClubRepository = canonicalClubRepository;
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

        var federatedResponse = clubRepository.findFederatedClubById(query.getFederatedClubId())
                .flatMap(club -> findDetails(club, query))
                .map(DomainQueryResponse::sucessResponse)
                .orElse(null);
        if (federatedResponse != null) {
            return federatedResponse;
        }
        if (canonicalClubRepository == null) {
            return DomainQueryResponse.failResponse(null);
        }
        return canonicalClubRepository.findClubById(query.getFederatedClubId())
                .flatMap(club -> findCanonicalDetails(club, query))
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private java.util.Optional<FederatedClubCompetitionDetailsReadModel> findCanonicalDetails(
            Club club,
            FindFederatedClubCompetitionDetailsQuery query) {
        List<Team> teams = new java.util.ArrayList<>();
        for (FederatedClub federatedClub : clubRepository.findAllFederatedClubsByClubId(club.getId())) {
            teams.addAll(teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId()).stream()
                    .filter(team -> Objects.equals(federatedClub.getSource(), team.getSource()))
                    .filter(team -> query.getSeason().equals(team.getSeason()))
                    .toList());
        }
        if (teams.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<UUID> teamIds = teams.stream().map(Team::getId).distinct().toList();
        List<Match> matches = new java.util.ArrayList<>();
        for (ImportSource source : teams.stream()
                .map(Team::getSource)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList()) {
            matches.addAll(matchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                    teams.stream()
                            .filter(team -> source.equals(team.getSource()))
                            .map(Team::getId)
                            .toList(),
                    source,
                    query.getSeason(),
                    query.getCompetition().trim()));
        }
        if (matches.isEmpty()) {
            return java.util.Optional.empty();
        }
        ImportSource source = matches.stream().map(Match::getSource).filter(Objects::nonNull).findFirst().orElse(null);
        return java.util.Optional.of(new FederatedClubCompetitionDetailsReadModel(
                club.getId(),
                club.getName(),
                source,
                query.getCompetition().trim(),
                query.getSeason(),
                matches.stream()
                        .sorted(Comparator.comparing(Match::getRound).thenComparing(Match::getId))
                        .map(match -> toReadModel(match, teamIds))
                        .toList(),
                club.getId(),
                club.getName()));
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
                        .toList(),
                club.getClub().map(canonical -> canonical.getId()).orElse(null),
                club.getClub().map(canonical -> canonical.getName()).orElse(null)));
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
