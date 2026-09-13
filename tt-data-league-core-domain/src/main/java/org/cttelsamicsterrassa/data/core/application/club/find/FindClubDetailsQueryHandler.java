package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubPlayerReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.FederatedClubTeamReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.PlayerCompetitionResultReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
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
    private final GameRepository gameRepository;
    private final DoublesPairRepository doublesPairRepository;

    @Inject
    public FindClubDetailsQueryHandler(
            ClubRepository clubRepository,
            FederatedClubRepository federatedClubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository,
            PlayerSeasonRepository playerSeasonRepository,
            GameRepository gameRepository,
            DoublesPairRepository doublesPairRepository) {
        this.clubRepository = clubRepository;
        this.federatedClubRepository = federatedClubRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.gameRepository = gameRepository;
        this.doublesPairRepository = doublesPairRepository;
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

        List<PlayerSeason> distinctPlayerSeasons = playerSeasons.stream()
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
                .toList();

        Map<UUID, Map<String, Totals>> playerResultsByCompetition =
                summarizePlayerResultsByCompetition(matches);
        List<FederatedClubPlayerReadModel> playerModels = distinctPlayerSeasons.stream()
                .map(playerSeason -> toPlayerReadModel(
                        playerSeason,
                        playerCompetitions.getOrDefault(playerSeason.getId(), List.of()),
                        playerResultsByCompetition.getOrDefault(playerSeason.getId(), Map.of())))
                .toList();

        List<ClubCompetitionReadModel> competitionModels = summarizeCompetitions(matches, teams);
        return new ClubDetailsReadModel(
                club.getId(), club.getName(), federatedModels, teamModels, competitionModels, playerModels);
    }

    private FederatedClubPlayerReadModel toPlayerReadModel(
            PlayerSeason playerSeason,
            List<String> competitions,
            Map<String, Totals> resultsByCompetition) {
        Totals overall = new Totals();
        List<PlayerCompetitionResultReadModel> competitionResults = resultsByCompetition.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(entry -> {
                    Totals totals = entry.getValue();
                    overall.matchCount += totals.matchCount;
                    overall.wins += totals.wins;
                    overall.draws += totals.draws;
                    overall.losses += totals.losses;
                    return new PlayerCompetitionResultReadModel(
                            entry.getKey(), totals.matchCount, totals.wins, totals.draws, totals.losses);
                })
                .toList();
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
                        .orElse(null),
                overall.matchCount,
                overall.wins,
                overall.draws,
                overall.losses,
                competitionResults);
    }

    /**
     * A player's win/loss record is based on their own individual games (singles rubbers, or their
     * doubles pair's rubbers), not on whether their team won the overall match: a player who lost
     * both their rubbers in a team win should not be credited with a win.
     */
    private Map<UUID, Map<String, Totals>> summarizePlayerResultsByCompetition(List<Match> matches) {
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        List<Game> games = gameRepository.findGamesByMatchIds(matchIds);
        Map<UUID, List<DoublesPair>> pairsByGameId = doublesPairRepository
                .findDoublesPairsByGameIds(games.stream().map(Game::getId).toList()).stream()
                .filter(pair -> pair.getGame() != null && pair.getPlayer() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        pair -> pair.getGame().getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));

        Map<UUID, Map<String, Totals>> totalsByPlayerSeasonId = new LinkedHashMap<>();
        for (Game game : games) {
            if (game.getWinnerSide() == null || game.getMatch() == null) {
                continue;
            }
            String competition = Objects.requireNonNullElse(game.getMatch().getCompetition(), "");
            if ("DOUBLES".equals(game.getType())) {
                for (DoublesPair pair : pairsByGameId.getOrDefault(game.getId(), List.of())) {
                    boolean won = game.getWinnerSide().equals(pair.getSide());
                    record(totalsByPlayerSeasonId, pair.getPlayer().getId(), competition, won);
                }
            } else {
                if (game.getHomePlayer() != null) {
                    record(totalsByPlayerSeasonId, game.getHomePlayer().getId(), competition,
                            "HOME".equals(game.getWinnerSide()));
                }
                if (game.getAwayPlayer() != null) {
                    record(totalsByPlayerSeasonId, game.getAwayPlayer().getId(), competition,
                            "AWAY".equals(game.getWinnerSide()));
                }
            }
        }
        return totalsByPlayerSeasonId;
    }

    private void record(
            Map<UUID, Map<String, Totals>> totalsByPlayerSeasonId, UUID playerSeasonId, String competition,
            boolean won) {
        Map<String, Totals> byCompetition = totalsByPlayerSeasonId.computeIfAbsent(
                playerSeasonId, ignored -> new LinkedHashMap<>());
        Totals current = byCompetition.computeIfAbsent(competition, ignored -> new Totals());
        current.matchCount++;
        if (won) {
            current.wins++;
        } else {
            current.losses++;
        }
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
