package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubTeamReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named
public class FindFederatedClubDetailsQueryHandler
        extends DomainQueryHandler<FindFederatedClubDetailsQuery, FederatedClubDetailsReadModel> {

    private final FederatedClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    @Inject
    public FindFederatedClubDetailsQueryHandler(
            FederatedClubRepository clubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            PlayerSeasonRepository playerSeasonRepository) {
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public DomainQueryResponse<FederatedClubDetailsReadModel> handle(FindFederatedClubDetailsQuery query) {
        if (query.getFederatedClubId() == null) {
            return DomainQueryResponse.failResponse(null);
        }

        return clubRepository.findFederatedClubById(query.getFederatedClubId())
                .map(this::composeDetails)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private FederatedClubDetailsReadModel composeDetails(FederatedClub club) {
        List<Team> teams = teamRepository.findAllTeamsByFederatedClubId(club.getId()).stream()
                .filter(team -> club.getSource().equals(team.getSource()))
                .toList();
        List<FederatedClubTeamReadModel> teamModels = teams.stream()
                .sorted(Comparator.comparing(
                                Team::getSeason,
                                Comparator.nullsLast(Comparator.comparing(Season::toString)))
                        .thenComparing(Team::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Team::getId))
                .map(team -> new FederatedClubTeamReadModel(
                        team.getId(), team.getName(), team.getSource(), team.getSeason()))
                .toList();

        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        List<PlayerSeason> playerSeasons = teamIds.isEmpty()
                ? List.of()
                : playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(teamIds, club.getSource());
        List<Match> matches = teamIds.isEmpty()
                ? List.of()
                : matchRepository.findAllMatchesByTeamIdsAndSource(teamIds, club.getSource());
        Map<UUID, List<String>> playerCompetitions = teamIds.isEmpty()
                ? Map.of()
                : playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
                        teamIds, club.getSource());
        List<FederatedClubPlayerReadModel> players = playerSeasons.stream()
                .map(playerSeason -> toPlayerReadModel(
                        playerSeason,
                        playerCompetitions.getOrDefault(playerSeason.getId(), List.of())))
                .toList();
        List<FederatedClubCompetitionReadModel> competitions = summarizeCompetitions(matches, teamIds);

        return new FederatedClubDetailsReadModel(
                club.getId(), club.getName(), club.getSource(), teamModels, competitions, players,
                club.getClub().map(canonical -> canonical.getId()).orElse(null),
                club.getClub().map(canonical -> canonical.getName()).orElse(null));
    }

    private FederatedClubPlayerReadModel toPlayerReadModel(
            PlayerSeason playerSeason,
            List<String> competitions) {
        return new FederatedClubPlayerReadModel(
                playerSeason.getId(),
                playerSeason.getFederatedPlayer().map(player -> player.getId()).orElse(null),
                playerSeason.getFederatedPlayer().map(player -> player.getName()).orElse(null),
                playerSeason.getName(),
                playerSeason.getLicense(),
                playerSeason.getSource(),
                playerSeason.getSeason(),
                competitions,
                playerSeason.getFederatedPlayer()
                        .flatMap(player -> player.getPlayer().map(canonical -> canonical.getId()))
                        .orElse(null),
                playerSeason.getFederatedPlayer()
                        .flatMap(player -> player.getPlayer().map(canonical -> canonical.getName()))
                        .orElse(null));
    }

    private List<FederatedClubCompetitionReadModel> summarizeCompetitions(
            List<Match> matches, List<UUID> clubTeamIds) {
        Map<CompetitionKey, Totals> totals = new LinkedHashMap<>();
        for (Match match : Objects.requireNonNull(matches, "matches must not be null")) {
            if (match.getSeason() == null) {
                continue;
            }
            boolean involvesClub = clubTeamIds.contains(teamId(match.getHomeTeam()))
                    || clubTeamIds.contains(teamId(match.getAwayTeam()));
            if (!involvesClub) {
                continue;
            }

            CompetitionKey key = new CompetitionKey(match.getCompetition(), match.getSeason());
            Totals current = totals.computeIfAbsent(key, ignored -> new Totals());
            current.matchCount++;
            UUID winnerId = teamId(match.getWinnerTeam());
            if (winnerId == null) {
                current.draws++;
            } else if (clubTeamIds.contains(winnerId)) {
                current.wins++;
            } else {
                current.losses++;
            }
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(
                                        CompetitionKey::season,
                                        Comparator.comparing(Season::toString))
                                .thenComparing(CompetitionKey::name,
                                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))))
                .map(entry -> new FederatedClubCompetitionReadModel(
                        entry.getKey().name(),
                        entry.getKey().season(),
                        entry.getValue().matchCount,
                        entry.getValue().wins,
                        entry.getValue().draws,
                        entry.getValue().losses))
                .toList();
    }

    private static UUID teamId(Team team) {
        return team == null ? null : team.getId();
    }

    private record CompetitionKey(String name, Season season) {
    }

    private static final class Totals {
        private int matchCount;
        private int wins;
        private int draws;
        private int losses;
    }
}
