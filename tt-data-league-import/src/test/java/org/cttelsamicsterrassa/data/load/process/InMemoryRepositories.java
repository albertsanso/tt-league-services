package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory stand-ins for the persistence ports, enforcing the same natural keys as the schema so
 * that the idempotency the processors rely on is actually exercised.
 */
public final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    public static final class Clubs implements FederatedClubRepository {
        final Map<UUID, FederatedClub> byId = new LinkedHashMap<>();

        @Override
        public Optional<FederatedClub> findFederatedClubById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<FederatedClub> findFederatedClubBySourceAndName(ImportSource source, String name) {
            return byId.values().stream()
                    .filter(club -> Objects.equals(club.getSource(), source) && Objects.equals(club.getName(), name))
                    .findFirst();
        }

        @Override
        public void saveFederatedClub(FederatedClub club) {
            byId.put(club.getId(), club);
        }

        @Override
        public void deleteFederatedClubById(UUID id) {
            byId.remove(id);
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsByFragmentsInName(List<String> fragments) {
            return byId.values().stream()
                    .filter(club -> containsAllFragments(club.getName(), fragments))
                    .toList();
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsBySourceAndFragmentsInName(ImportSource source, List<String> fragments) {
            return byId.values().stream()
                    .filter(club -> Objects.equals(club.getSource(), source))
                    .filter(club -> containsAllFragments(club.getName(), fragments))
                    .toList();
        }

        public int size() {
            return byId.size();
        }

        private static boolean containsAllFragments(String name, List<String> fragments) {
            return name != null && fragments != null && !fragments.isEmpty()
                    && fragments.stream().allMatch(fragment ->
                    fragment != null && name.toLowerCase().contains(fragment.toLowerCase()));
        }
    }

    public static final class Teams implements TeamRepository {
        final Map<UUID, Team> byId = new LinkedHashMap<>();

        @Override
        public Optional<Team> findTeamById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Team> findTeamByNameAndSeasonAndSource(String name, Season season, ImportSource source) {
            return byId.values().stream()
                    .filter(cs -> Objects.equals(cs.getName(), name))
                    .filter(cs -> season.equals(cs.getSeason()))
                    .filter(cs -> Objects.equals(cs.getSource(), source))
                    .findFirst();
        }

        @Override
        public Optional<Team> findTeamByFederatedClubAndSeason(UUID clubId, Season season) {
            return byId.values().stream()
                    .filter(cs -> cs.getFederatedClub().map(club -> clubId.equals(club.getId())).orElse(false))
                    .filter(cs -> season.equals(cs.getSeason()))
                    .findFirst();
        }

        @Override
        public List<Team> findAllTeamsByFederatedClubId(UUID clubId) {
            return byId.values().stream()
                    .filter(team -> team.getFederatedClub().map(club -> clubId.equals(club.getId())).orElse(false))
                    .toList();
        }

        @Override
        public List<Team> findAllTeamsBySource(ImportSource source) {
            return byId.values().stream()
                    .filter(cs -> Objects.equals(cs.getSource(), source))
                    .toList();
        }

        @Override
        public void saveTeam(Team team) {
            byId.put(team.getId(), team);
        }

        @Override
        public void deleteTeamById(UUID id) {
            byId.remove(id);
        }

        @Override
        public List<Team> findAllTeamsBySimilarName(String name) {
            return byId.values().stream()
                    .filter(cs -> cs.getName() != null && cs.getName().contains(name))
                    .toList();
        }

        @Override
        public List<Team> findAllTeamsBySimilarNameAndSeason(String name, Season season) {
            return findAllTeamsBySimilarName(name).stream()
                    .filter(cs -> season.equals(cs.getSeason()))
                    .toList();
        }

        @Override
        public List<Team> findAllTeamsBySimilarNameAndSeasonAndSource(String name, Season season, ImportSource source) {
            return findAllTeamsBySimilarNameAndSeason(name, season).stream()
                    .filter(cs -> Objects.equals(cs.getSource(), source))
                    .toList();
        }
    }

    public static final class Players implements FederatedPlayerRepository {
        public final Map<UUID, FederatedPlayer> byId = new LinkedHashMap<>();

        @Override
        public Optional<FederatedPlayer> findFederatedPlayerById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<FederatedPlayer> findFederatedPlayerBySourceAndName(ImportSource source, String name) {
            Objects.requireNonNull(source, "source must not be null");
            List<FederatedPlayer> matches = byId.values().stream()
                    .filter(p -> Objects.equals(p.getSource(), source) && Objects.equals(p.getName(), name))
                    .toList();
            if (matches.size() > 1) {
                throw new IllegalStateException(
                        "Multiple federated players found for source and name: " + source + ", " + name);
            }
            return matches.stream().findFirst();
        }

        @Override
        public void saveFederatedPlayer(FederatedPlayer player) {
            byId.put(player.getId(), player);
        }

        @Override
        public void deleteFederatedPlayerById(UUID id) {
            byId.remove(id);
        }

        @Override
        public List<FederatedPlayer> findAllFederatedPlayersByFragmentsInName(List<String> fragments) {
            return byId.values().stream()
                    .filter(player -> player.getName() != null && fragments != null && !fragments.isEmpty()
                            && fragments.stream().allMatch(fragment ->
                            fragment != null && player.getName().toLowerCase().contains(fragment.toLowerCase())))
                    .toList();
        }
    }

    public static final class PlayerSeasons implements PlayerSeasonRepository {
        final Map<UUID, PlayerSeason> byId = new LinkedHashMap<>();
        private final Map<UUID, List<UUID>> playerSeasonIdsByTeam = new LinkedHashMap<>();
        private final Map<UUID, LinkedHashSet<String>> competitionsByPlayerSeason = new LinkedHashMap<>();

        @Override
        public Optional<PlayerSeason> findPlayerSeasonById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<PlayerSeason> findPlayerSeasonByLicenseAndSeason(ImportSource source, String license, Season season) {
            return byId.values().stream()
                    .filter(ps -> Objects.equals(ps.getSource(), source)
                            && Objects.equals(ps.getLicense(), license)
                            && season.equals(ps.getSeason()))
                    .findFirst();
        }

        @Override
        public List<PlayerSeason> findAllPlayerSeasonsBySource(ImportSource source) {
            return byId.values().stream()
                    .filter(ps -> Objects.equals(ps.getSource(), source))
                    .toList();
        }

        @Override
        public List<PlayerSeason> findAllPlayerSeasonsByTeamIdsAndSource(
                java.util.Collection<UUID> teamIds,
                ImportSource source) {
            if (teamIds == null || teamIds.isEmpty()) {
                return List.of();
            }
            return byId.values().stream()
                    .filter(playerSeason -> Objects.equals(playerSeason.getSource(), source))
                    .filter(playerSeason -> teamIds.stream()
                            .anyMatch(teamId -> playerSeasonIdsByTeam
                                    .getOrDefault(teamId, List.of())
                                    .contains(playerSeason.getId())))
                    .toList();
        }

        @Override
        public Map<UUID, List<String>> findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
                java.util.Collection<UUID> teamIds,
                ImportSource source) {
            if (teamIds == null || teamIds.isEmpty()) {
                return Map.of();
            }
            return byId.values().stream()
                    .filter(playerSeason -> Objects.equals(playerSeason.getSource(), source))
                    .filter(playerSeason -> teamIds.stream()
                            .anyMatch(teamId -> playerSeasonIdsByTeam
                                    .getOrDefault(teamId, List.of())
                                    .contains(playerSeason.getId())))
                    .collect(Collectors.toMap(
                            PlayerSeason::getId,
                            playerSeason -> List.copyOf(
                                    competitionsByPlayerSeason.getOrDefault(
                                            playerSeason.getId(), new LinkedHashSet<>()))));
        }

        void associateLineups(List<Lineup> lineups) {
            lineups.stream()
                    .filter(lineup -> lineup.getTeam() != null && lineup.getPlayer() != null)
                    .forEach(lineup -> playerSeasonIdsByTeam
                            .computeIfAbsent(lineup.getTeam().getId(), ignored -> new ArrayList<>())
                            .add(lineup.getPlayer().getId()));
            lineups.stream()
                    .filter(lineup -> lineup.getPlayer() != null
                            && lineup.getMatch() != null
                            && lineup.getMatch().getCompetition() != null
                            && Objects.equals(lineup.getMatch().getSource(), lineup.getPlayer().getSource())
                            && Objects.equals(lineup.getMatch().getSeason(), lineup.getPlayer().getSeason()))
                    .forEach(lineup -> competitionsByPlayerSeason
                            .computeIfAbsent(lineup.getPlayer().getId(), ignored -> new LinkedHashSet<>())
                            .add(lineup.getMatch().getCompetition()));
        }

        @Override
        public void savePlayerSeason(PlayerSeason playerSeason) {
            byId.put(playerSeason.getId(), playerSeason);
        }

        @Override
        public void deletePlayerSeasonById(UUID id) {
            byId.remove(id);
        }
    }

    static final class Matches implements MatchRepository {
        final List<Match> saved = new ArrayList<>();

        @Override
        public Optional<Match> findMatchById(UUID id) {
            return saved.stream().filter(m -> id.equals(m.getId())).findFirst();
        }

        @Override
        public Optional<Match> findMatchByExternalId(String externalId) {
            return saved.stream().filter(m -> Objects.equals(m.getExternalId(), externalId)).findFirst();
        }

        @Override
        public Optional<Match> findMatchByNaturalKey(String competition,
                                                     Season season,
                                                     int groupNumber,
                                                     int round,
                                                     UUID homeTeamId,
                                                     UUID awayTeamId) {
            return saved.stream()
                    .filter(m -> competition.equals(m.getCompetition()))
                    .filter(m -> season.equals(m.getSeason()))
                    .filter(m -> m.getGroupNumber() == groupNumber && m.getRound() == round)
                    .filter(m -> homeTeamId.equals(m.getHomeTeam().getId()))
                    .filter(m -> awayTeamId.equals(m.getAwayTeam().getId()))
                    .findFirst();
        }

        @Override
        public List<Match> findAllMatchesByTeamIds(java.util.Collection<UUID> teamIds) {
            return saved.stream()
                    .filter(match -> teamIds.contains(match.getHomeTeam().getId())
                            || teamIds.contains(match.getAwayTeam().getId()))
                    .toList();
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSource(
                java.util.Collection<UUID> teamIds,
                ImportSource source) {
            return findAllMatchesByTeamIds(teamIds).stream()
                    .filter(match -> Objects.equals(match.getSource(), source))
                    .toList();
        }

        @Override
        public List<Match> findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition(
                java.util.Collection<UUID> teamIds,
                ImportSource source,
                Season season,
                String competition) {
            return findAllMatchesByTeamIds(teamIds).stream()
                    .filter(match -> Objects.equals(match.getSource(), source))
                    .filter(match -> Objects.equals(match.getSeason(), season))
                    .filter(match -> Objects.equals(match.getCompetition(), competition))
                    .toList();
        }

        @Override
        public void saveMatch(Match match) {
            saved.add(match);
        }
    }

    static final class Lineups implements LineupRepository {
        final List<Lineup> saved = new ArrayList<>();
        private final PlayerSeasons playerSeasons;

        Lineups() {
            this(null);
        }

        Lineups(PlayerSeasons playerSeasons) {
            this.playerSeasons = playerSeasons;
        }

        @Override
        public List<Lineup> findLineupsByMatchId(UUID matchId) {
            return saved.stream().filter(l -> matchId.equals(l.getMatch().getId())).toList();
        }

        @Override
        public void saveLineups(List<Lineup> lineups) {
            saved.addAll(lineups);
            if (playerSeasons != null) {
                playerSeasons.associateLineups(lineups);
            }
        }
    }

    static final class Games implements GameRepository {
        final List<Game> saved = new ArrayList<>();

        @Override
        public List<Game> findGamesByMatchId(UUID matchId) {
            return saved.stream().filter(g -> matchId.equals(g.getMatch().getId())).toList();
        }

        @Override
        public void saveGames(List<Game> games) {
            saved.addAll(games);
        }
    }

    static final class SetScores implements SetScoreRepository {
        final List<SetScore> saved = new ArrayList<>();

        @Override
        public void saveSetScores(List<SetScore> setScores) {
            saved.addAll(setScores);
        }
    }

    static final class DoublesPairs implements DoublesPairRepository {
        final List<DoublesPair> saved = new ArrayList<>();

        @Override
        public void saveDoublesPairs(List<DoublesPair> doublesPairs) {
            saved.addAll(doublesPairs);
        }
    }
}
