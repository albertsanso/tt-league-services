package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class FindMatchDetailsQueryHandler
        extends DomainQueryHandler<FindMatchDetailsQuery, MatchDetailReadModel> {

    /**
     * Number of past matches considered for "recent form" (teams and players), and for the
     * previous window used to compute the trend note. Named so it can be tuned without a contract
     * change, per FEAT-00057's build plan.
     */
    private static final int RECENT_FORM_WINDOW = 5;

    private final MatchRepository matches;
    private final LineupRepository lineups;
    private final GameRepository games;
    private final SetScoreRepository sets;
    private final DoublesPairRepository doubles;
    private final PlayerSeasonRepository playerSeasons;
    private final FederatedPlayerRepository federatedPlayers;

    @Inject
    public FindMatchDetailsQueryHandler(MatchRepository matches, LineupRepository lineups,
                                        GameRepository games, SetScoreRepository sets,
                                        DoublesPairRepository doubles, PlayerSeasonRepository playerSeasons,
                                        FederatedPlayerRepository federatedPlayers) {
        this.matches = matches;
        this.lineups = lineups;
        this.games = games;
        this.sets = sets;
        this.doubles = doubles;
        this.playerSeasons = playerSeasons;
        this.federatedPlayers = federatedPlayers;
    }

    @Override
    public DomainQueryResponse<MatchDetailReadModel> handle(FindMatchDetailsQuery query) {
        if (query.getMatchId() == null) {
            return DomainQueryResponse.failResponse(null);
        }
        return matches.findMatchById(query.getMatchId())
                .map(this::compose)
                .map(DomainQueryResponse::sucessResponse)
                .orElseGet(() -> DomainQueryResponse.failResponse(null));
    }

    private MatchDetailReadModel compose(Match match) {
        List<Lineup> lineupValues = lineups.findLineupsByMatchId(match.getId());
        List<Game> gameValues = games.findGamesByMatchId(match.getId());
        List<UUID> gameIds = gameValues.stream().map(Game::getId).toList();
        Map<UUID, List<SetScore>> scoreByGame = sets.findSetScoresByGameIds(gameIds).stream()
                .collect(Collectors.groupingBy(score -> score.getGame().getId()));
        Map<UUID, List<DoublesPair>> pairsByGame = doubles.findDoublesPairsByGameIds(gameIds).stream()
                .collect(Collectors.groupingBy(pair -> pair.getGame().getId()));

        List<Match> homeTeamMatches = teamMatchesExcludingCurrent(match.getHomeTeam(), match);
        List<Match> awayTeamMatches = teamMatchesExcludingCurrent(match.getAwayTeam(), match);
        List<UUID> pastMatchIds = new ArrayList<>();
        homeTeamMatches.forEach(value -> pastMatchIds.add(value.getId()));
        awayTeamMatches.forEach(value -> pastMatchIds.add(value.getId()));
        Map<UUID, List<Lineup>> lineupsByMatchId = lineups.findAllLineupsByMatchIds(pastMatchIds).stream()
                .filter(value -> value.getMatch() != null)
                .collect(Collectors.groupingBy(value -> value.getMatch().getId()));

        Set<String> homeLineupKey = lineupKey(lineupValues, match.getHomeTeam() == null ? null : match.getHomeTeam().getId());
        Set<String> awayLineupKey = lineupKey(lineupValues, match.getAwayTeam() == null ? null : match.getAwayTeam().getId());

        MatchDetailReadModel.TeamFormReadModel homeForm = teamForm(match.getHomeTeam(), match, homeTeamMatches);
        MatchDetailReadModel.TeamFormReadModel awayForm = teamForm(match.getAwayTeam(), match, awayTeamMatches);

        MatchDetailReadModel.AlignmentStabilityReadModel homeAlignment = alignmentStability(
                match.getHomeTeam(), match, homeTeamMatches, homeLineupKey, lineupsByMatchId);
        MatchDetailReadModel.AlignmentStabilityReadModel awayAlignment = alignmentStability(
                match.getAwayTeam(), match, awayTeamMatches, awayLineupKey, lineupsByMatchId);

        List<MatchDetailReadModel.PlayerFormReadModel> playerFormValues = lineupValues.stream()
                .map(lineup -> playerForm(lineup, match)).filter(Objects::nonNull).toList();

        return new MatchDetailReadModel(match.getId(), match.getSource(), match.getExternalId(),
                match.getCompetition(), match.getSeason(), match.getGroupNumber(), match.getRound(),
                match.getPhase(),
                match.getDateTime(), match.getCity(), match.getVenue(), team(match.getHomeTeam()),
                team(match.getAwayTeam()), team(match.getWinnerTeam()), match.getRefereeName(),
                match.getRefereeLicense(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), match.getHomeSetsWon(),
                match.getAwaySetsWon(), match.isProtested(),
                lineupValues.stream().map(this::lineup).toList(),
                gameValues.stream().map(game -> game(game, scoreByGame.getOrDefault(game.getId(), List.of()),
                        pairsByGame.getOrDefault(game.getId(), List.of()))).toList(),
                homeForm, awayForm, playerFormValues, homeAlignment, awayAlignment);
    }

    /**
     * Returns a team's past matches, same source+season as {@code current}, excluding {@code
     * current} itself, ordered most-recent-first. Alignment-stability is deliberately scoped to the
     * same source+season as the viewed match, not aggregated across seasons.
     */
    private List<Match> teamMatchesExcludingCurrent(Team team, Match current) {
        if (team == null) {
            return List.of();
        }
        return matches.findAllMatchesByTeamIdsAndSource(List.of(team.getId()), current.getSource()).stream()
                .filter(value -> !value.getId().equals(current.getId())
                        && Objects.equals(value.getSeason(), current.getSeason()))
                .sorted(Comparator.comparing(Match::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private MatchDetailReadModel.TeamFormReadModel teamForm(Team team, Match current,
                                                            List<Match> sortedDescExcludingCurrent) {
        if (team == null) {
            return new MatchDetailReadModel.TeamFormReadModel(List.of(), null, null, null);
        }
        List<Match> lastWindow = sortedDescExcludingCurrent.stream().limit(RECENT_FORM_WINDOW).toList();
        List<Match> previousWindow = sortedDescExcludingCurrent.stream()
                .skip(RECENT_FORM_WINDOW).limit(RECENT_FORM_WINDOW).toList();
        List<MatchDetailReadModel.FormResultReadModel> lastResults = lastWindow.stream()
                .sorted(Comparator.comparing(Match::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(value -> formResult(value, team.getId())).toList();
        return new MatchDetailReadModel.TeamFormReadModel(lastResults, winRate(lastWindow, team.getId()),
                winRate(previousWindow, team.getId()),
                winRate(withCurrent(sortedDescExcludingCurrent, current), team.getId()));
    }

    private MatchDetailReadModel.AlignmentStabilityReadModel alignmentStability(
            Team team, Match current, List<Match> teamMatchesExcludingCurrent, Set<String> currentKey,
            Map<UUID, List<Lineup>> lineupsByMatchId) {
        List<Match> all = withCurrent(teamMatchesExcludingCurrent, current);
        UUID teamId = team == null ? null : team.getId();
        Double overallWinRate = winRate(all, teamId);
        if (team == null || currentKey.isEmpty()) {
            return new MatchDetailReadModel.AlignmentStabilityReadModel(1, 0, 0, 0, null, overallWinRate);
        }
        int timesFielded = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        for (Match value : all) {
            Set<String> key = value.getId().equals(current.getId())
                    ? currentKey
                    : lineupKey(lineupsByMatchId.getOrDefault(value.getId(), List.of()), teamId);
            if (key.isEmpty() || !key.equals(currentKey)) {
                continue;
            }
            timesFielded++;
            String result = resultFor(value, teamId);
            if ("win".equals(result)) {
                wins++;
            } else if ("loss".equals(result)) {
                losses++;
            } else if ("draw".equals(result)) {
                draws++;
            }
        }
        Double winRateValue = (wins + losses) == 0 ? null : wins * 100.0 / (wins + losses);
        return new MatchDetailReadModel.AlignmentStabilityReadModel(
                timesFielded, wins, draws, losses, winRateValue, overallWinRate);
    }

    private MatchDetailReadModel.PlayerFormReadModel playerForm(Lineup lineup, Match current) {
        PlayerSeason player = lineup.getPlayer();
        if (player == null) {
            return null;
        }
        UUID canonicalId = player.getFederatedPlayer()
                .flatMap(federatedPlayer -> federatedPlayer.getPlayer().map(Player::getId)).orElse(null);
        List<UUID> playerSeasonIds = resolvePlayerSeasonIds(player, canonicalId, current);
        Map<UUID, Lineup> lineupByMatchId = lineups.findAllLineupsByPlayerSeasonIds(playerSeasonIds).stream()
                .filter(value -> value.getMatch() != null)
                .sorted(Comparator.comparing(Lineup::getId))
                .collect(Collectors.toMap(value -> value.getMatch().getId(), value -> value,
                        (first, ignored) -> first, LinkedHashMap::new));
        List<Match> playerMatches = lineupByMatchId.values().stream().map(Lineup::getMatch)
                .filter(value -> !value.getId().equals(current.getId()))
                .sorted(Comparator.comparing(Match::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(RECENT_FORM_WINDOW)
                .toList();
        List<MatchDetailReadModel.FormResultReadModel> lastResults = playerMatches.stream()
                .sorted(Comparator.comparing(Match::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(value -> formResult(value, teamIdFor(lineupByMatchId.get(value.getId()))))
                .toList();
        int wins = 0;
        int losses = 0;
        for (Match value : playerMatches) {
            String result = resultFor(value, teamIdFor(lineupByMatchId.get(value.getId())));
            if ("win".equals(result)) {
                wins++;
            } else if ("loss".equals(result)) {
                losses++;
            }
        }
        Double winRateValue = (wins + losses) == 0 ? null : wins * 100.0 / (wins + losses);
        return new MatchDetailReadModel.PlayerFormReadModel(player.getId(), canonicalId, lastResults, winRateValue);
    }

    /**
     * Resolves which {@link PlayerSeason} registrations count toward a player's recent form: any
     * registration for the same canonical player, source and season as the viewed match (so a
     * mid-season club transfer's matches for the other club still count), matching {@code
     * PlayerDetailsDto.statistics[]}'s player-centric scoping. Falls back to the single registration
     * on the lineup when the player has not been consolidated to a canonical identity.
     */
    private List<UUID> resolvePlayerSeasonIds(PlayerSeason player, UUID canonicalId, Match current) {
        if (canonicalId == null) {
            return List.of(player.getId());
        }
        List<UUID> federatedIds = federatedPlayers.findAllFederatedPlayersByPlayerId(canonicalId).stream()
                .map(FederatedPlayer::getId).toList();
        List<UUID> scoped = federatedIds.isEmpty() ? List.of()
                : playerSeasons.findAllPlayerSeasonsByFederatedPlayerIds(federatedIds).stream()
                        .filter(registration -> registration.getSource() == current.getSource()
                                && Objects.equals(registration.getSeason(), current.getSeason()))
                        .map(PlayerSeason::getId).distinct().toList();
        return scoped.isEmpty() ? List.of(player.getId()) : scoped;
    }

    private UUID teamIdFor(Lineup lineup) {
        return lineup == null || lineup.getTeam() == null ? null : lineup.getTeam().getId();
    }

    private Set<String> lineupKey(List<Lineup> lineupsForMatch, UUID teamId) {
        if (teamId == null) {
            return Set.of();
        }
        return lineupsForMatch.stream()
                .filter(value -> value.getTeam() != null && teamId.equals(value.getTeam().getId()))
                .map(this::playerKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String playerKey(Lineup value) {
        PlayerSeason player = value.getPlayer();
        if (player == null) {
            return null;
        }
        UUID canonicalId = player.getFederatedPlayer()
                .flatMap(federatedPlayer -> federatedPlayer.getPlayer().map(Player::getId)).orElse(null);
        if (canonicalId != null) {
            return "player:" + canonicalId;
        }
        return player.getLicenseId() == null ? null : "license:" + player.getLicenseId();
    }

    private List<Match> withCurrent(List<Match> matchesExcludingCurrent, Match current) {
        List<Match> all = new ArrayList<>(matchesExcludingCurrent);
        all.add(current);
        return all;
    }

    private String resultFor(Match match, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        return match.getWinnerTeam() == null ? "draw"
                : teamId.equals(match.getWinnerTeam().getId()) ? "win" : "loss";
    }

    private Double winRate(List<Match> matchesForTeam, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        int wins = 0;
        int losses = 0;
        for (Match value : matchesForTeam) {
            String result = resultFor(value, teamId);
            if ("win".equals(result)) {
                wins++;
            } else if ("loss".equals(result)) {
                losses++;
            }
        }
        return (wins + losses) == 0 ? null : wins * 100.0 / (wins + losses);
    }

    private MatchDetailReadModel.FormResultReadModel formResult(Match match, UUID teamId) {
        boolean isHome = teamId != null && match.getHomeTeam() != null && teamId.equals(match.getHomeTeam().getId());
        String opponent = isHome
                ? (match.getAwayTeam() == null ? null : match.getAwayTeam().getName())
                : (match.getHomeTeam() == null ? null : match.getHomeTeam().getName());
        Integer teamScore = isHome ? match.getHomeGamesWon() : match.getAwayGamesWon();
        Integer opponentScore = isHome ? match.getAwayGamesWon() : match.getHomeGamesWon();
        String score = teamScore == null || opponentScore == null ? null : teamScore + "-" + opponentScore;
        return new MatchDetailReadModel.FormResultReadModel(match.getId(), match.getDateTime(), opponent,
                resultFor(match, teamId), score);
    }

    private MatchDetailReadModel.TeamReadModel team(Team team) {
        return team == null ? null : new MatchDetailReadModel.TeamReadModel(team.getId(), team.getName(),
                team.getSource() == null ? null : team.getSource().name(),
                team.getSeason() == null ? null : team.getSeason().toString(),
                team.getFederatedClub().map(FederatedClub::getId).orElse(null));
    }

    private MatchDetailReadModel.PlayerReadModel player(PlayerSeason player) {
        if (player == null) return null;
        UUID federatedId = player.getFederatedPlayer().map(value -> value.getId()).orElse(null);
        UUID canonicalId = player.getFederatedPlayer().flatMap(value -> value.getPlayer()
                .map(canonical -> canonical.getId())).orElse(null);
        return new MatchDetailReadModel.PlayerReadModel(player.getId(), federatedId, canonicalId,
                player.getName(), player.getLicenseId(),
                player.getSource() == null ? null : player.getSource().name(),
                player.getSeason() == null ? null : player.getSeason().toString());
    }

    private MatchDetailReadModel.LineupReadModel lineup(Lineup value) {
        return new MatchDetailReadModel.LineupReadModel(value.getId(), team(value.getTeam()),
                value.getLetter(), value.getPosition(), player(value.getPlayer()), value.getRanking());
    }

    private MatchDetailReadModel.GameReadModel game(Game value, List<SetScore> scores,
                                                     List<DoublesPair> pairs) {
        return new MatchDetailReadModel.GameReadModel(value.getId(), value.getGameNumber(), value.getType(),
                value.getCrossover(), player(value.getHomePlayer()), player(value.getAwayPlayer()),
                value.getHomeSetsWon(), value.getAwaySetsWon(), value.getWinnerSide(),
                value.getCumulativeHomeSetsWon(), value.getCumulativeAwaySetsWon(), value.isNotPlayed(),
                value.getReason(), scores.stream().map(score -> new MatchDetailReadModel.SetReadModel(
                        score.getId(), score.getSetNumber(), score.getHomePoints(), score.getAwayPoints())).toList(),
                pairs.stream().map(pair -> new MatchDetailReadModel.DoublesPlayerReadModel(
                        pair.getId(), pair.getSide(), player(pair.getPlayer()))).toList());
    }
}
