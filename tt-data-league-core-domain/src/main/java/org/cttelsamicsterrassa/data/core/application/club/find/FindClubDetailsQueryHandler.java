package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named
public class FindClubDetailsQueryHandler
        extends DomainQueryHandler<FindClubDetailsQuery, ClubDetailsReadModel> {

    private final ClubRepository clubRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    @Inject
    public FindClubDetailsQueryHandler(
            ClubRepository clubRepository,
            FederatedClubRepository federatedClubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            PlayerSeasonRepository playerSeasonRepository) {
        this.clubRepository = clubRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public DomainQueryResponse<ClubDetailsReadModel> handle(FindClubDetailsQuery query) {
        if (query.getClubId() == null) {
            return DomainQueryResponse.failResponse(null);
        }
        return clubRepository.findClubById(query.getClubId())
                .map(this::composeDetails)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private ClubDetailsReadModel composeDetails(Club club) {
        List<FederatedClub> federatedClubs = federatedClubRepository
                .findAllFederatedClubsByClubId(club.getId()).stream()
                .sorted(Comparator.comparing(FederatedClub::getSource,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(FederatedClub::getName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedClub::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedClub::getId))
                .toList();

        List<ClubFederatedReadModel> federatedModels = federatedClubs.stream()
                .map(federatedClub -> new ClubFederatedReadModel(
                        federatedClub.getId(), federatedClub.getName(), federatedClub.getSource()))
                .toList();
        Map<UUID, Team> teamsById = new LinkedHashMap<>();
        for (FederatedClub federatedClub : federatedClubs) {
            teamRepository.findAllTeamsByFederatedClubId(federatedClub.getId()).stream()
                    .filter(team -> Objects.equals(federatedClub.getSource(), team.getSource()))
                    .forEach(team -> teamsById.putIfAbsent(team.getId(), team));
        }

        List<Team> teams = teamsById.values().stream()
                .sorted(Comparator.comparing(Team::getSeason,
                                Comparator.nullsLast(Comparator.comparing(Season::toString)))
                        .thenComparing(Team::getSource,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(Team::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Team::getId))
                .toList();
        List<FederatedClubTeamReadModel> teamModels = teams.stream()
                .map(team -> new FederatedClubTeamReadModel(
                        team.getId(), team.getName(), team.getSource(), team.getSeason()))
                .toList();

        Map<ImportSource, List<UUID>> teamIdsBySource = new EnumMap<>(ImportSource.class);
        for (Team team : teams) {
            if (team.getSource() != null) {
                teamIdsBySource.computeIfAbsent(team.getSource(), ignored -> new ArrayList<>()).add(team.getId());
            }
        }

        List<PlayerSeason> playerSeasons = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        Map<UUID, List<String>> playerCompetitions = new LinkedHashMap<>();
        for (Map.Entry<ImportSource, List<UUID>> entry : teamIdsBySource.entrySet()) {
            ImportSource source = entry.getKey();
            List<UUID> teamIds = entry.getValue();
            playerSeasons.addAll(playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(teamIds, source));
            matches.addAll(matchRepository.findAllMatchesByTeamIdsAndSource(teamIds, source));
            playerSeasonRepository.findAllPlayerSeasonCompetitionsByTeamIdsAndSource(teamIds, source)
                    .forEach((playerSeasonId, competitions) -> playerCompetitions
                            .computeIfAbsent(playerSeasonId, ignored -> new ArrayList<>())
                            .addAll(competitions));
        }

        List<FederatedClubPlayerReadModel> playerModels = playerSeasons.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlayerSeason::getId,
                        playerSeason -> playerSeason,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(PlayerSeason::getSeason,
                                Comparator.nullsLast(Comparator.comparing(Season::toString)))
                        .thenComparing(PlayerSeason::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PlayerSeason::getId))
                .map(playerSeason -> toPlayerReadModel(
                        playerSeason,
                        playerCompetitions.getOrDefault(playerSeason.getId(), List.of())))
                .toList();

        List<ClubCompetitionReadModel> competitionModels = summarizeCompetitions(matches, teams);
        return new ClubDetailsReadModel(
                club.getId(), club.getName(), federatedModels, teamModels, competitionModels, playerModels);
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
                competitions.stream().distinct().sorted().toList(),
                playerSeason.getFederatedPlayer()
                        .flatMap(player -> player.getPlayer().map(canonical -> canonical.getId()))
                        .orElse(null),
                playerSeason.getFederatedPlayer()
                        .flatMap(player -> player.getPlayer().map(canonical -> canonical.getName()))
                        .orElse(null));
    }

    private List<ClubCompetitionReadModel> summarizeCompetitions(List<Match> matches, List<Team> teams) {
        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        Map<CompetitionKey, Totals> totals = new LinkedHashMap<>();
        for (Match match : Objects.requireNonNull(matches, "matches must not be null")) {
            if (match.getSeason() == null || !teamIds.contains(teamId(match.getHomeTeam()))
                    && !teamIds.contains(teamId(match.getAwayTeam()))) {
                continue;
            }
            CompetitionKey key = new CompetitionKey(
                    match.getCompetition(), match.getSource(), match.getSeason());
            Totals current = totals.computeIfAbsent(key, ignored -> new Totals());
            current.matchCount++;
            UUID winnerId = teamId(match.getWinnerTeam());
            if (winnerId == null) {
                current.draws++;
            } else if (teamIds.contains(winnerId)) {
                current.wins++;
            } else {
                current.losses++;
            }
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(CompetitionKey::season, Comparator.comparing(Season::toString))
                        .thenComparing(CompetitionKey::source,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(CompetitionKey::name,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CompetitionKey::name, Comparator.nullsLast(String::compareTo))))
                .map(entry -> new ClubCompetitionReadModel(
                        entry.getKey().name(),
                        entry.getKey().source(),
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

    private record CompetitionKey(String name, ImportSource source, Season season) {
    }

    private static final class Totals {
        private int matchCount;
        private int wins;
        private int draws;
        private int losses;
    }
}
