package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
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
public class FindClubDetailsQueryHandler
        extends DomainQueryHandler<FindClubDetailsQuery, ClubDetailsReadModel> {

    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    @Inject
    public FindClubDetailsQueryHandler(
            ClubRepository clubRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository) {
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
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
        List<Team> teams = teamRepository.findAllTeamsByClubId(club.getId());
        List<ClubTeamReadModel> teamModels = teams.stream()
                .sorted(Comparator.comparing(
                                Team::getSeason,
                                Comparator.nullsLast(Comparator.comparing(Season::toString)))
                        .thenComparing(Team::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Team::getId))
                .map(team -> new ClubTeamReadModel(
                        team.getId(), team.getName(), team.getSource(), team.getSeason()))
                .toList();

        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        List<Match> matches = teamIds.isEmpty()
                ? List.of()
                : matchRepository.findAllMatchesByTeamIds(teamIds);
        List<ClubCompetitionReadModel> competitions = summarizeCompetitions(matches, teamIds);

        return new ClubDetailsReadModel(
                club.getId(), club.getName(), club.getSource(), teamModels, competitions);
    }

    private List<ClubCompetitionReadModel> summarizeCompetitions(
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
                .map(entry -> new ClubCompetitionReadModel(
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
