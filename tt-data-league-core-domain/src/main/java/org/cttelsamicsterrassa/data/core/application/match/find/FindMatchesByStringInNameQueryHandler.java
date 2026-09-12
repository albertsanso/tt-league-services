package org.cttelsamicsterrassa.data.core.application.match.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.match.find.dto.MatchSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named
public class FindMatchesByStringInNameQueryHandler
        extends DomainQueryHandler<FindMatchesByStringInNameQuery, List<MatchSearchReadModel>> {

    private static final int MAX_RESULTS = 5;

    private final MatchRepository repository;
    private final LineupRepository lineups;

    @Inject
    public FindMatchesByStringInNameQueryHandler(MatchRepository repository, LineupRepository lineups) {
        this.repository = repository;
        this.lineups = lineups;
    }

    @Override
    public DomainQueryResponse<List<MatchSearchReadModel>> handle(FindMatchesByStringInNameQuery query) {
        String search = query.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }
        List<Match> matches = repository.findAllMatchesByFragmentsInName(
                List.of(search.trim().split("\\s+")), MAX_RESULTS);

        Map<UUID, List<Lineup>> lineupsByMatch = lineups.findAllLineupsByMatchIds(
                        matches.stream().map(Match::getId).toList()).stream()
                .collect(Collectors.groupingBy(value -> value.getMatch().getId()));

        List<MatchSearchReadModel> results = matches.stream()
                .map(match -> SearchMatchesQueryHandler.toReadModel(match,
                        lineupsByMatch.getOrDefault(match.getId(), List.of())))
                .sorted(Comparator.comparing(MatchSearchReadModel::dateTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MatchSearchReadModel::id))
                .toList();
        return DomainQueryResponse.sucessResponse(results);
    }
}
