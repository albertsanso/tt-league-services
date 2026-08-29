package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerClubReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerGameReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerOpponentReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerRegistrationReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSeasonStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class FindPlayerDetailsQueryHandler extends DomainQueryHandler<FindPlayerDetailsQuery, PlayerDetailsReadModel> {
    private final PlayerRepository playerRepository;
    private final FederatedPlayerRepository federatedPlayerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final LineupRepository lineupRepository;
    private final GameRepository gameRepository;
    private final DoublesPairRepository doublesPairRepository;

    @Inject
    public FindPlayerDetailsQueryHandler(PlayerRepository playerRepository,
                                         FederatedPlayerRepository federatedPlayerRepository,
                                         PlayerSeasonRepository playerSeasonRepository,
                                         LineupRepository lineupRepository,
                                         GameRepository gameRepository,
                                         DoublesPairRepository doublesPairRepository) {
        this.playerRepository = playerRepository;
        this.federatedPlayerRepository = federatedPlayerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.lineupRepository = lineupRepository;
        this.gameRepository = gameRepository;
        this.doublesPairRepository = doublesPairRepository;
    }

    public FindPlayerDetailsQueryHandler(PlayerRepository playerRepository,
                                         FederatedPlayerRepository federatedPlayerRepository,
                                         PlayerSeasonRepository playerSeasonRepository,
                                         LineupRepository lineupRepository) {
        this(playerRepository, federatedPlayerRepository, playerSeasonRepository, lineupRepository, null, null);
    }

    @Override
    public DomainQueryResponse<PlayerDetailsReadModel> handle(FindPlayerDetailsQuery query) {
        if (query.getPlayerId() == null) {
            return DomainQueryResponse.failResponse(null);
        }
        return playerRepository.findPlayerById(query.getPlayerId()).map(player -> compose(player, query))
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private PlayerDetailsReadModel compose(Player player, FindPlayerDetailsQuery query) {
        List<FederatedPlayer> federated = federatedPlayerRepository.findAllFederatedPlayersByPlayerId(player.getId())
                .stream().sorted(Comparator.comparing(FederatedPlayer::getSource,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(FederatedPlayer::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedPlayer::getId)).toList();
        List<PlayerSeason> registrations = playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds(
                        federated.stream().map(FederatedPlayer::getId).toList()).stream()
                .sorted(Comparator.comparing(PlayerSeason::getSeason,
                                Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(PlayerSeason::getSource, Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(PlayerSeason::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PlayerSeason::getId)).toList();
        Map<UUID, PlayerSeason> registrationById = registrations.stream()
                .collect(Collectors.toMap(PlayerSeason::getId, value -> value, (first, ignored) -> first, LinkedHashMap::new));
        List<Lineup> lineups = lineupRepository.findAllLineupsByPlayerSeasonIds(
                        registrations.stream().map(PlayerSeason::getId).toList()).stream()
                .filter(lineup -> {
                    PlayerSeason registration = registrationById.get(lineup.getPlayer().getId());
                    return registration != null && lineup.getMatch() != null
                            && registration.getSource() == lineup.getMatch().getSource()
                            && (lineup.getSource() == null || registration.getSource() == lineup.getSource());
                }).toList();
        List<Match> allPlayerMatches = lineups.stream().map(Lineup::getMatch).distinct()
                .sorted(Comparator.comparing(Match::getSeason, Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(Match::getCompetition, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Match::getRound).thenComparing(Match::getId)).toList();
        List<Match> playerMatches = allPlayerMatches.stream()
                .filter(match -> matchesFilter(match, query)).toList();
        Map<UUID, List<Game>> gamesByMatch = loadGames(playerMatches);
        Map<UUID, List<DoublesPair>> pairsByGame = loadDoublesPairs(gamesByMatch);
        List<PlayerSeasonStatisticsReadModel> statistics = playerMatches.stream()
                .collect(Collectors.groupingBy(match -> new StatisticsKey(match.getSource(), match.getSeason()),
                        LinkedHashMap::new, Collectors.toList())).entrySet().stream()
                .map(entry -> toStatistics(entry.getKey(), entry.getValue(), lineups))
                .sorted(Comparator.comparing(PlayerSeasonStatisticsReadModel::season,
                                Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(PlayerSeasonStatisticsReadModel::source,
                                Comparator.nullsLast(Comparator.comparing(Enum::name))))
                .toList();
        List<PlayerMatchReadModel> matches = playerMatches.stream()
                .map(match -> toMatch(match, lineups, registrations, gamesByMatch, pairsByGame)).toList();
        List<PlayerClubReadModel> clubs = lineups.stream().map(Lineup::getTeam)
                .filter(team -> team != null && team.getFederatedClub().isPresent()).map(this::toClub).distinct()
                .sorted(Comparator.comparing(PlayerClubReadModel::season,
                                Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(PlayerClubReadModel::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PlayerClubReadModel::id)).toList();
        List<PlayerCompetitionReadModel> competitions = allPlayerMatches.stream().collect(Collectors.groupingBy(
                        match -> new CompetitionKey(match.getCompetition(), match.getSource(), match.getSeason()),
                        LinkedHashMap::new, Collectors.counting())).entrySet().stream()
                .map(entry -> new PlayerCompetitionReadModel(entry.getKey().name(), entry.getKey().source(),
                        entry.getKey().season(), entry.getValue().intValue())).toList();
        return new PlayerDetailsReadModel(player.getId(), player.getName(),
                federated.stream().map(value -> new PlayerFederatedReadModel(value.getId(), value.getName(),
                        value.getLicenseId(), value.getSource())).toList(),
                registrations.stream().map(value -> new PlayerRegistrationReadModel(value.getId(), value.getName(),
                        value.getLicenseId(), value.getSeason(), value.getSource(),
                        value.getFederatedPlayer().map(FederatedPlayer::getId).orElse(null))).toList(),
                clubs, competitions, matches, statistics);
    }

    private boolean matchesFilter(Match match, FindPlayerDetailsQuery query) {
        return (query.getSource() == null || query.getSource() == match.getSource())
                && (query.getSeason() == null || Objects.equals(query.getSeason(), match.getSeason()))
                && (query.getCompetition() == null || Objects.equals(query.getCompetition(), match.getCompetition()));
    }

    private PlayerClubReadModel toClub(Team team) {
        var club = team.getFederatedClub().orElseThrow();
        return new PlayerClubReadModel(club.getId(), club.getName(), team.getSource(), team.getSeason());
    }

    private PlayerMatchReadModel toMatch(Match match, List<Lineup> lineups, List<PlayerSeason> registrations,
                                         Map<UUID, List<Game>> gamesByMatch,
                                         Map<UUID, List<DoublesPair>> pairsByGame) {
        Team playerTeam = lineups.stream().filter(lineup -> lineup.getMatch().getId().equals(match.getId()))
                .map(Lineup::getTeam).filter(team -> team != null).findFirst().orElse(null);
        UUID teamId = playerTeam == null ? null : playerTeam.getId();
        String result = match.getWinnerTeam() == null ? "draw"
                : match.getWinnerTeam().getId().equals(teamId) ? "win" : "loss";
        Integer playerGamesWon = teamId == null ? null
                : match.getHomeTeam().getId().equals(teamId) ? match.getHomeGamesWon()
                : match.getAwayTeam().getId().equals(teamId) ? match.getAwayGamesWon() : null;
        List<PlayerGameReadModel> games = gameRepository == null ? List.of()
                : gamesForMatch(registrations.stream().map(PlayerSeason::getId).collect(Collectors.toSet()),
                gamesByMatch.getOrDefault(match.getId(), List.of()), pairsByGame);
        return new PlayerMatchReadModel(match.getId(), match.getSource(), match.getCompetition(), match.getSeason(),
                match.getRound(), match.getDateTime(), match.getHomeTeam().getName(), match.getAwayTeam().getName(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), result, playerGamesWon,
                playerTeam == null ? null : playerTeam.getName(), games);
    }

    private Map<UUID, List<Game>> loadGames(List<Match> matches) {
        if (gameRepository == null || matches.isEmpty()) {
            return Map.of();
        }
        return gameRepository.findGamesByMatchIds(matches.stream().map(Match::getId).toList()).stream()
                .sorted(Comparator.comparing((Game game) -> game.getMatch().getId())
                        .thenComparing(Game::getGameNumber).thenComparing(Game::getId))
                .collect(Collectors.groupingBy(game -> game.getMatch().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<UUID, List<DoublesPair>> loadDoublesPairs(Map<UUID, List<Game>> gamesByMatch) {
        List<UUID> gameIds = gamesByMatch.values().stream().flatMap(List::stream).map(Game::getId).toList();
        if (doublesPairRepository == null || gameIds.isEmpty()) {
            return Map.of();
        }
        return doublesPairRepository.findDoublesPairsByGameIds(gameIds).stream()
                .collect(Collectors.groupingBy(pair -> pair.getGame().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private List<PlayerGameReadModel> gamesForMatch(Collection<UUID> playerSeasonIds, List<Game> games,
                                                    Map<UUID, List<DoublesPair>> pairsByGame) {
        return games.stream().map(game -> toGame(game, playerSeasonIds, pairsByGame.getOrDefault(game.getId(), List.of())))
                .toList();
    }

    private PlayerGameReadModel toGame(Game game, Collection<UUID> playerSeasonIds,
                                       List<DoublesPair> pairs) {
        String selectedSide = selectedSide(game, playerSeasonIds, pairs);
        List<PlayerSeason> candidates = opponents(game, selectedSide, pairs);
        Map<String, PlayerOpponentReadModel> unique = new LinkedHashMap<>();
        candidates.forEach(player -> unique.putIfAbsent(opponentKey(player), toOpponent(player)));
        if (selectedSide == null || unique.isEmpty()) {
            return new PlayerGameReadModel(game.getId(), game.getGameNumber(), game.getType(),
                    "unavailable", game.getHomeSetsWon(), game.getAwaySetsWon(), List.of(),
                    "No s’ha pogut resoldre l’oponent amb identitat de font.");
        }
        String result = game.getWinnerSide() == null
                ? (game.isNotPlayed() ? "unavailable" : "draw")
                : game.getWinnerSide().equals(selectedSide) ? "win" : "loss";
        return new PlayerGameReadModel(game.getId(), game.getGameNumber(), game.getType(), result,
                game.getHomeSetsWon(), game.getAwaySetsWon(), List.copyOf(unique.values()), null);
    }

    private String selectedSide(Game game, Collection<UUID> playerSeasonIds, List<DoublesPair> pairs) {
        if ("DOUBLES".equals(game.getType())) {
            return pairs.stream().filter(pair -> playerSeasonIds.contains(pair.getPlayer().getId()))
                    .map(DoublesPair::getSide).findFirst().orElse(null);
        }
        if (game.getHomePlayer() != null && playerSeasonIds.contains(game.getHomePlayer().getId())) {
            return "HOME";
        }
        if (game.getAwayPlayer() != null && playerSeasonIds.contains(game.getAwayPlayer().getId())) {
            return "AWAY";
        }
        return null;
    }

    private List<PlayerSeason> opponents(Game game, String selectedSide, List<DoublesPair> pairs) {
        if (selectedSide == null) {
            return List.of();
        }
        if ("DOUBLES".equals(game.getType())) {
            return pairs.stream().filter(pair -> !selectedSide.equals(pair.getSide()))
                    .map(DoublesPair::getPlayer).filter(Objects::nonNull).toList();
        }
        PlayerSeason opponent = "HOME".equals(selectedSide) ? game.getAwayPlayer() : game.getHomePlayer();
        return opponent == null ? List.of() : List.of(opponent);
    }

    private PlayerOpponentReadModel toOpponent(PlayerSeason player) {
        FederatedPlayer federated = player.getFederatedPlayer().orElse(null);
        UUID federatedId = federated == null ? null : federated.getId();
        UUID canonicalId = federated == null ? null : federated.getPlayer().map(Player::getId).orElse(null);
        return new PlayerOpponentReadModel(canonicalId, federatedId, player.getId(),
                player.getName(), player.getSource(), player.getSeason(), true);
    }

    private String opponentKey(PlayerSeason player) {
        FederatedPlayer federated = player.getFederatedPlayer().orElse(null);
        UUID canonicalId = federated == null ? null : federated.getPlayer().map(Player::getId).orElse(null);
        UUID federatedId = federated == null ? null : federated.getId();
        return (canonicalId != null ? "player:" + canonicalId
                : federatedId != null ? "federated:" + federatedId : "season:" + player.getId());
    }

    private PlayerSeasonStatisticsReadModel toStatistics(StatisticsKey key, List<Match> matches,
                                                         List<Lineup> lineups) {
        int wins = 0;
        int losses = 0;
        int scoredMatches = 0;
        int scoreTotal = 0;
        for (Match match : matches) {
            UUID teamId = lineups.stream()
                    .filter(lineup -> lineup.getMatch().getId().equals(match.getId()))
                    .map(Lineup::getTeam).filter(team -> team != null).map(Team::getId).findFirst().orElse(null);
            if (teamId != null && match.getWinnerTeam() != null) {
                if (match.getWinnerTeam().getId().equals(teamId)) {
                    wins++;
                } else {
                    losses++;
                }
            }
            Integer score = teamId == null ? null
                    : match.getHomeTeam().getId().equals(teamId) ? match.getHomeGamesWon()
                    : match.getAwayTeam().getId().equals(teamId) ? match.getAwayGamesWon() : null;
            if (score != null && match.getHomeGamesWon() != null && match.getAwayGamesWon() != null) {
                scoredMatches++;
                scoreTotal += score;
            }
        }
        int decidedMatches = wins + losses;
        Double winPercentage = decidedMatches == 0 ? null : wins * 100.0 / decidedMatches;
        Double averageScore = scoredMatches == 0 ? null : scoreTotal * 1.0 / scoredMatches;
        return new PlayerSeasonStatisticsReadModel(key.source(), key.season(), matches.size(), wins, losses,
                winPercentage, averageScore);
    }

    private record CompetitionKey(String name,
                                  org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource source,
                                  org.cttelsamicsterrassa.data.core.domain.shared.model.Season season) {
    }

    private record StatisticsKey(
            org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource source,
            org.cttelsamicsterrassa.data.core.domain.shared.model.Season season) {
    }
}
