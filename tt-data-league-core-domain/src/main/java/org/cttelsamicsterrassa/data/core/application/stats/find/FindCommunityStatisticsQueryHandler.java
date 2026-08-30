package org.cttelsamicsterrassa.data.core.application.stats.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.CommunityStatisticsReadModel;
import org.cttelsamicsterrassa.data.core.application.stats.find.dto.SeasonAvailability;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

/**
 * Computes the community-wide statistics overview from the documented aggregate repository
 * queries. The current season is the most recent season with imported matches, across every source.
 */
@Named
public class FindCommunityStatisticsQueryHandler
        extends DomainQueryHandler<FindCommunityStatisticsQuery, CommunityStatisticsReadModel> {

    private final FederatedPlayerRepository players;
    private final FederatedClubRepository clubs;
    private final MatchRepository matches;

    @Inject
    public FindCommunityStatisticsQueryHandler(FederatedPlayerRepository players, FederatedClubRepository clubs,
                                                MatchRepository matches) {
        this.players = players;
        this.clubs = clubs;
        this.matches = matches;
    }

    @Override
    public DomainQueryResponse<CommunityStatisticsReadModel> handle(FindCommunityStatisticsQuery query) {
        long totalPlayers = players.countUniquePlayerNames();
        long totalClubs = clubs.countUniqueClubNames();
        long totalMatches = matches.countAllMatches();

        List<String> seasons = matches.findAllSeasons();
        String currentSeasonLabel = seasons.isEmpty() ? null : seasons.getFirst();

        SeasonAvailability status = SeasonAvailability.UNAVAILABLE;

        if (currentSeasonLabel != null) {
            status = SeasonAvailability.IN_PROGRESS;
        }

        return DomainQueryResponse.sucessResponse(new CommunityStatisticsReadModel(
                new CommunityStatisticsReadModel.CountSummary(totalPlayers),
                new CommunityStatisticsReadModel.CountSummary(totalClubs),
                new CommunityStatisticsReadModel.CountSummary(totalMatches),
                new CommunityStatisticsReadModel.CurrentSeasonSummary(currentSeasonLabel, status)));
    }
}
