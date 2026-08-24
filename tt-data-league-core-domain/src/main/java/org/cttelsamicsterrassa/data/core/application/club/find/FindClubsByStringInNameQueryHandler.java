package org.cttelsamicsterrassa.data.core.application.club.find;

import org.albertsanso.commons.query.DomainQueryHandler;
import org.albertsanso.commons.query.DomainQueryResponse;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubCompetitionReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubFederatedReadModel;
import org.cttelsamicsterrassa.data.core.application.club.find.dto.ClubSearchReadModel;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named
public class FindClubsByStringInNameQueryHandler
        extends DomainQueryHandler<FindClubsByStringInNameQuery, List<ClubSearchReadModel>> {

    private final ClubRepository clubRepository;
    private final FederatedClubRepository federatedClubRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    public FindClubsByStringInNameQueryHandler(
            ClubRepository clubRepository,
            FederatedClubRepository federatedClubRepository) {
        this(clubRepository, federatedClubRepository, null, null, null);
    }

    @Inject
    public FindClubsByStringInNameQueryHandler(
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
    public DomainQueryResponse<List<ClubSearchReadModel>> handle(FindClubsByStringInNameQuery query) {
        String search = query.getStringToSearch();
        if (search == null || search.trim().length() < 2) {
            return DomainQueryResponse.failResponse(List.of());
        }

        List<String> fragments = List.of(search.trim().toLowerCase(Locale.ROOT).split("\\s+"));
        ImportSource source = query.getSource();
        List<FederatedClub> matchingFederatedClubs = source == null
                ? federatedClubRepository.findAllFederatedClubsByFragmentsInName(fragments)
                : federatedClubRepository.findAllFederatedClubsBySourceAndFragmentsInName(source, fragments);

        Map<UUID, Club> clubsById = new LinkedHashMap<>();
        clubRepository.findAllClubs().stream()
                .filter(club -> containsAllFragments(club.getName(), fragments))
                .forEach(club -> clubsById.put(club.getId(), club));
        matchingFederatedClubs.stream()
                .map(club -> club.getClub().orElse(null))
                .filter(java.util.Objects::nonNull)
                .forEach(club -> clubsById.putIfAbsent(club.getId(), club));

        List<ClubSearchReadModel> results = clubsById.values().stream()
                .filter(club -> source == null || hasFederatedClubForSource(club, source))
                .map(club -> toReadModel(club, source))
                .sorted(Comparator.comparing(ClubSearchReadModel::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ClubSearchReadModel::name)
                        .thenComparing(ClubSearchReadModel::id))
                .toList();
        return DomainQueryResponse.sucessResponse(results);
    }

    private ClubSearchReadModel toReadModel(Club club, ImportSource source) {
        List<FederatedClub> federatedClubs = federatedClubRepository.findAllFederatedClubsByClubId(club.getId()).stream()
                .filter(federatedClub -> source == null || source.equals(federatedClub.getSource()))
                .sorted(Comparator.comparing(FederatedClub::getName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(FederatedClub::getName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(FederatedClub::getSource,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(FederatedClub::getId))
                .toList();
        if (teamRepository == null || matchRepository == null || playerSeasonRepository == null) {
            return new ClubSearchReadModel(
                    club.getId(),
                    club.getName(),
                    federatedClubs.stream()
                            .map(federatedClub -> new ClubFederatedReadModel(
                                    federatedClub.getId(), federatedClub.getName(), federatedClub.getSource()))
                            .toList());
        }

        Map<UUID, Team> teamsById = new LinkedHashMap<>();
        federatedClubs.forEach(federatedClub -> teamRepository
                .findAllTeamsByFederatedClubId(federatedClub.getId()).stream()
                .filter(team -> Objects.equals(federatedClub.getSource(), team.getSource()))
                .forEach(team -> teamsById.putIfAbsent(team.getId(), team)));
        List<Team> teams = teamsById.values().stream().toList();
        Map<ImportSource, List<UUID>> teamIdsBySource = new java.util.EnumMap<>(ImportSource.class);
        teams.forEach(team -> {
            if (team.getSource() != null) {
                teamIdsBySource.computeIfAbsent(team.getSource(), ignored -> new java.util.ArrayList<>())
                        .add(team.getId());
            }
        });
        List<Match> matches = new java.util.ArrayList<>();
        Map<UUID, PlayerSeason> players = new LinkedHashMap<>();
        for (Map.Entry<ImportSource, List<UUID>> entry : teamIdsBySource.entrySet()) {
            matches.addAll(matchRepository.findAllMatchesByTeamIdsAndSource(entry.getValue(), entry.getKey()));
            playerSeasonRepository.findAllPlayerSeasonsByTeamIdsAndSource(entry.getValue(), entry.getKey())
                    .forEach(player -> players.putIfAbsent(player.getId(), player));
        }
        return new ClubSearchReadModel(
                club.getId(),
                club.getName(),
                federatedClubs.stream()
                        .map(federatedClub -> new ClubFederatedReadModel(
                                federatedClub.getId(), federatedClub.getName(), federatedClub.getSource()))
                        .toList(),
                summarizeCompetitions(matches, teams),
                players.size(),
                seasons(teams, players.values(), matches));
    }

    private boolean hasFederatedClubForSource(Club club, ImportSource source) {
        return federatedClubRepository.findAllFederatedClubsByClubId(club.getId()).stream()
                .anyMatch(federatedClub -> source.equals(federatedClub.getSource()));
    }

    private static boolean containsAllFragments(String name, List<String> fragments) {
        if (name == null) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        return fragments.stream().allMatch(lowerName::contains);
    }

    private static List<ClubCompetitionReadModel> summarizeCompetitions(List<Match> matches, List<Team> teams) {
        List<UUID> teamIds = teams.stream().map(Team::getId).toList();
        Map<CompetitionKey, Totals> totals = new LinkedHashMap<>();
        matches.forEach(match -> {
            if (match.getSeason() == null || (!teamIds.contains(teamId(match.getHomeTeam()))
                    && !teamIds.contains(teamId(match.getAwayTeam())))) {
                return;
            }
            CompetitionKey key = new CompetitionKey(match.getCompetition(), match.getSource(), match.getSeason());
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
        });
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(CompetitionKey::season, Comparator.comparing(Season::toString))
                        .thenComparing(CompetitionKey::source,
                                Comparator.nullsLast(Comparator.comparing(Enum::name)))
                        .thenComparing(CompetitionKey::name,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CompetitionKey::name, Comparator.nullsLast(String::compareTo))))
                .map(entry -> new ClubCompetitionReadModel(
                        entry.getKey().name(), entry.getKey().source(), entry.getKey().season(),
                        entry.getValue().matchCount, entry.getValue().wins,
                        entry.getValue().draws, entry.getValue().losses))
                .toList();
    }

    private static List<Season> seasons(
            List<Team> teams,
            java.util.Collection<PlayerSeason> players,
            List<Match> matches) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                teams.stream().map(Team::getSeason),
                                players.stream().map(PlayerSeason::getSeason)),
                        matches.stream().map(Match::getSeason))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Season::toString))
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
