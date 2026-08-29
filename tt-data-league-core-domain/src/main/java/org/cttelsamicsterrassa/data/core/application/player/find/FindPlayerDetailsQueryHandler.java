package org.cttelsamicsterrassa.data.core.application.player.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerClubReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerDetailsReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerMatchReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerRegistrationReadModel;
import org.cttelsamicsterrassa.data.core.application.player.find.dto.PlayerSeasonStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class FindPlayerDetailsQueryHandler extends DomainQueryHandler<FindPlayerDetailsQuery, PlayerDetailsReadModel> {
    private final PlayerRepository playerRepository;
    private final FederatedPlayerRepository federatedPlayerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final LineupRepository lineupRepository;

    @Inject
    public FindPlayerDetailsQueryHandler(PlayerRepository playerRepository,
                                         FederatedPlayerRepository federatedPlayerRepository,
                                         PlayerSeasonRepository playerSeasonRepository,
                                         LineupRepository lineupRepository) {
        this.playerRepository = playerRepository;
        this.federatedPlayerRepository = federatedPlayerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.lineupRepository = lineupRepository;
    }

    @Override
    public DomainQueryResponse<PlayerDetailsReadModel> handle(FindPlayerDetailsQuery query) {
        if (query.getPlayerId() == null) {
            return DomainQueryResponse.failResponse(null);
        }
        return playerRepository.findPlayerById(query.getPlayerId()).map(this::compose)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private PlayerDetailsReadModel compose(Player player) {
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
        List<Match> playerMatches = lineups.stream().map(Lineup::getMatch).distinct()
                .sorted(Comparator.comparing(Match::getSeason, Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(Match::getCompetition, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Match::getRound).thenComparing(Match::getId)).toList();
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
                .map(match -> toMatch(match, lineups)).toList();
        List<PlayerClubReadModel> clubs = lineups.stream().map(Lineup::getTeam)
                .filter(team -> team != null && team.getFederatedClub().isPresent()).map(this::toClub).distinct()
                .sorted(Comparator.comparing(PlayerClubReadModel::season,
                                Comparator.nullsLast(Comparator.comparing(Object::toString)))
                        .thenComparing(PlayerClubReadModel::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(PlayerClubReadModel::id)).toList();
        List<PlayerCompetitionReadModel> competitions = playerMatches.stream().collect(Collectors.groupingBy(
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

    private PlayerClubReadModel toClub(Team team) {
        var club = team.getFederatedClub().orElseThrow();
        return new PlayerClubReadModel(club.getId(), club.getName(), team.getSource(), team.getSeason());
    }

    private PlayerMatchReadModel toMatch(Match match, List<Lineup> lineups) {
        Team playerTeam = lineups.stream().filter(lineup -> lineup.getMatch().getId().equals(match.getId()))
                .map(Lineup::getTeam).filter(team -> team != null).findFirst().orElse(null);
        UUID teamId = playerTeam == null ? null : playerTeam.getId();
        String result = match.getWinnerTeam() == null ? "draw"
                : match.getWinnerTeam().getId().equals(teamId) ? "win" : "loss";
        Integer playerGamesWon = teamId == null ? null
                : match.getHomeTeam().getId().equals(teamId) ? match.getHomeGamesWon()
                : match.getAwayTeam().getId().equals(teamId) ? match.getAwayGamesWon() : null;
        return new PlayerMatchReadModel(match.getId(), match.getSource(), match.getCompetition(), match.getSeason(),
                match.getRound(), match.getDateTime(), match.getHomeTeam().getName(), match.getAwayTeam().getName(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), result, playerGamesWon,
                playerTeam == null ? null : playerTeam.getName());
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
