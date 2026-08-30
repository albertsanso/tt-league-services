package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchDetailReadModel;
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
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Named
public class FindMatchDetailsQueryHandler
        extends DomainQueryHandler<FindMatchDetailsQuery, MatchDetailReadModel> {
    private final MatchRepository matches;
    private final LineupRepository lineups;
    private final GameRepository games;
    private final SetScoreRepository sets;
    private final DoublesPairRepository doubles;

    @Inject
    public FindMatchDetailsQueryHandler(MatchRepository matches, LineupRepository lineups,
                                        GameRepository games, SetScoreRepository sets,
                                        DoublesPairRepository doubles) {
        this.matches = matches;
        this.lineups = lineups;
        this.games = games;
        this.sets = sets;
        this.doubles = doubles;
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
        return new MatchDetailReadModel(match.getId(), match.getSource(), match.getExternalId(),
                match.getCompetition(), match.getSeason(), match.getGroupNumber(), match.getRound(),
                match.getDateTime(), match.getCity(), match.getVenue(), team(match.getHomeTeam()),
                team(match.getAwayTeam()), team(match.getWinnerTeam()), match.getRefereeName(),
                match.getRefereeLicense(),
                match.getHomeGamesWon(), match.getAwayGamesWon(), match.getHomeSetsWon(),
                match.getAwaySetsWon(), match.isProtested(),
                lineupValues.stream().map(this::lineup).toList(),
                gameValues.stream().map(game -> game(game, scoreByGame.getOrDefault(game.getId(), List.of()),
                        pairsByGame.getOrDefault(game.getId(), List.of()))).toList());
    }

    private MatchDetailReadModel.TeamReadModel team(Team team) {
        return team == null ? null : new MatchDetailReadModel.TeamReadModel(team.getId(), team.getName(),
                team.getSource() == null ? null : team.getSource().name(),
                team.getSeason() == null ? null : team.getSeason().toString());
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
