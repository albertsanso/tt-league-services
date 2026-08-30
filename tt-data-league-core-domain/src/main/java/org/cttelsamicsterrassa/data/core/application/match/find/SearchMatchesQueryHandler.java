package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchPage;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class SearchMatchesQueryHandler extends DomainQueryHandler<SearchMatchesQuery, MatchSearchPage> {
    private final MatchRepository repository;
    private final LineupRepository lineups;

    @Inject
    public SearchMatchesQueryHandler(MatchRepository repository, LineupRepository lineups) {
        this.repository = repository;
        this.lineups = lineups;
    }

    @Override
    public DomainQueryResponse<MatchSearchPage> handle(SearchMatchesQuery query) {
        try {
            var criteria = query.getCriteria();
            var matches = repository.searchMatches(criteria);
            Map<UUID, List<Lineup>> lineupsByMatch = lineups.findAllLineupsByMatchIds(
                            matches.stream().map(Match::getId).toList()).stream()
                    .collect(Collectors.groupingBy(value -> value.getMatch().getId()));
            var models = matches.stream().map(match -> toReadModel(match,
                            lineupsByMatch.getOrDefault(match.getId(), List.of())))
                    .sorted(Comparator.comparing(MatchSearchReadModel::dateTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(MatchSearchReadModel::id))
                    .toList();
            long total = repository.countMatches(criteria);
            return DomainQueryResponse.sucessResponse(
                    new MatchSearchPage(models, total, criteria.page(), criteria.pageSize(),
                            (long) (criteria.page() + 1) * criteria.pageSize() < total));
        } catch (IllegalArgumentException exception) {
            return DomainQueryResponse.failResponse(null);
        }
    }

    private static MatchSearchReadModel toReadModel(Match match, List<Lineup> lineups) {
        var homePlayers = lineups.stream().filter(value -> value.getTeam() != null
                        && match.getHomeTeam() != null && match.getHomeTeam().getId().equals(value.getTeam().getId()))
                .map(SearchMatchesQueryHandler::player).toList();
        var awayPlayers = lineups.stream().filter(value -> value.getTeam() != null
                        && match.getAwayTeam() != null && match.getAwayTeam().getId().equals(value.getTeam().getId()))
                .map(SearchMatchesQueryHandler::player).toList();
        return new MatchSearchReadModel(match.getId(), match.getSource(), match.getCompetition(),
                match.getSeason(), match.getRound(), match.getDateTime(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getName(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getName(),
                match.getWinnerTeam() == null ? null : match.getWinnerTeam().getName(),
                match.getHomeGamesWon(), match.getAwayGamesWon(),
                match.getHomeSetsWon(), match.getAwaySetsWon(), match.isProtested(), homePlayers, awayPlayers);
    }

    private static MatchSearchReadModel.PlayerReadModel player(Lineup lineup) {
        var player = lineup.getPlayer();
        return new MatchSearchReadModel.PlayerReadModel(player.getId(), player.getName(), player.getLicenseId());
    }
}
