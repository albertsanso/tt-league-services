package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundStatusCount;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillWriteResult;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.ScheduledMatchBackfillRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
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
        public List<FederatedClub> findAllFederatedClubsBySource(ImportSource source) {
            return byId.values().stream()
                    .filter(club -> Objects.equals(club.getSource(), source))
                    .sorted(java.util.Comparator.comparing(FederatedClub::getName,
                                    java.util.Comparator.nullsFirst(String::compareTo))
                            .thenComparing(club -> club.getId().toString()))
                    .toList();
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsByClubId(UUID clubId) {
            return byId.values().stream()
                    .filter(club -> club.getClub().map(canonical -> canonical.getId().equals(clubId)).orElse(false))
                    .toList();
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

    public static final class CanonicalClubs implements ClubRepository {
        final Map<UUID, Club> byId = new LinkedHashMap<>();

        @Override
        public Optional<Club> findClubById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Club> findClubByExactName(String name) {
            List<Club> matches = byId.values().stream()
                    .filter(club -> Objects.equals(club.getName(), name))
                    .toList();
            if (matches.size() > 1) {
                throw new IllegalStateException("Multiple canonical clubs found for name: " + name);
            }
            return matches.stream().findFirst();
        }

        @Override
        public List<Club> findAllClubs() {
            return byId.values().stream()
                    .sorted(java.util.Comparator.comparing(Club::getName).thenComparing(club -> club.getId().toString()))
                    .toList();
        }

        @Override
        public void saveClub(Club club) {
            byId.put(club.getId(), club);
        }

        @Override
        public void deleteClubById(UUID id) {
            byId.remove(id);
        }

        public int size() {
            return byId.size();
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

        public static final class CanonicalPlayers implements PlayerRepository {
            public final Map<UUID, Player> byId = new LinkedHashMap<>();

            @Override
            public Optional<Player> findPlayerById(UUID id) {
                return Optional.ofNullable(byId.get(id));
            }

            @Override
            public Optional<Player> findPlayerByExactName(String name) {
                List<Player> matches = byId.values().stream()
                        .filter(player -> Objects.equals(player.getName(), name))
                        .toList();
                if (matches.size() > 1) {
                    throw new IllegalStateException("Multiple canonical players found for name: " + name);
                }
                return matches.stream().findFirst();
            }

            @Override
            public Optional<Player> findFirstPlayerByNameFragments(List<String> fragments) {
                return Optional.empty();
            }

            @Override
            public void savePlayer(Player player) {
                byId.put(player.getId(), player);
            }

            @Override
            public void deletePlayerById(UUID id) {
                byId.remove(id);
            }
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
        public Optional<FederatedPlayer> findFederatedPlayerBySourceAndLicenseId(ImportSource source, String licenseId) {
            Objects.requireNonNull(source, "source must not be null");
            List<FederatedPlayer> matches = byId.values().stream()
                    .filter(player -> Objects.equals(player.getSource(), source)
                            && Objects.equals(player.getLicenseId(), licenseId))
                    .toList();
            if (matches.size() > 1) {
                throw new IllegalStateException(
                        "Multiple federated players found for source and licenseId: " + source + ", " + licenseId);
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
        public Optional<PlayerSeason> findPlayerSeasonBySourceLicenseAndSeason(ImportSource source, String license, Season season) {
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

    public static final class Matches implements MatchRepository {
        public final List<Match> saved = new ArrayList<>();
        private final Lineups lineups;
        private final Games games;
        private final SetScores setScores;
        private final DoublesPairs doublesPairs;

        Matches() {
            this(null, null, null, null);
        }

        public Matches(Lineups lineups, Games games, SetScores setScores, DoublesPairs doublesPairs) {
            this.lineups = lineups;
            this.games = games;
            this.setScores = setScores;
            this.doublesPairs = doublesPairs;
        }

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
                                                     Integer groupNumber,
                                                     int round,
                                                     String phase,
                                                     UUID homeTeamId,
                                                     UUID awayTeamId) {
            return saved.stream()
                    .filter(m -> competition.equals(m.getCompetition()))
                    .filter(m -> season.equals(m.getSeason()))
                    .filter(m -> Objects.equals(groupNumber, m.getGroupNumber()) && m.getRound() == round)
                    .filter(m -> Objects.equals(phase, m.getPhase()))
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
        public Optional<Match> findBySourceFixtureId(ImportSource source, String sourceFixtureId) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(sourceFixtureId, "sourceFixtureId");
            return saved.stream()
                    .filter(m -> source.equals(m.getSource())
                            && sourceFixtureId.equals(m.getSourceFixtureId()))
                    .findFirst();
        }

        @Override
        public void saveMatch(Match match) {
            if (match.getSourceFixtureId() != null) {
                boolean duplicate = saved.stream().anyMatch(other ->
                        !other.getId().equals(match.getId())
                                && Objects.equals(other.getSource(), match.getSource())
                                && match.getSourceFixtureId().equals(other.getSourceFixtureId()));
                if (duplicate) {
                    throw new IllegalStateException("Duplicate (source, sourceFixtureId): "
                            + match.getSource() + ", " + match.getSourceFixtureId());
                }
            }
            saved.add(match);
        }

        @Override
        public void replaceMatchContent(MatchContent content) {
            if (lineups == null || games == null || setScores == null || doublesPairs == null) {
                throw new IllegalStateException(
                        "replaceMatchContent requires the wired child stores (lineups, games, set scores, "
                                + "doubles pairs)");
            }
            Match replacement = content.match();
            int index = indexOfMatch(replacement.getId());
            if (!saved.get(index).hasSameNaturalKeyAs(replacement)) {
                throw new IllegalStateException("Replacement must not change the natural key of match "
                        + replacement.getId());
            }
            List<UUID> gameIds = games.saved.stream()
                    .filter(game -> replacement.getId().equals(game.getMatch().getId()))
                    .map(Game::getId)
                    .toList();
            doublesPairs.saved.removeIf(pair -> gameIds.contains(pair.getGame().getId()));
            setScores.saved.removeIf(setScore -> gameIds.contains(setScore.getGame().getId()));
            games.saved.removeIf(game -> replacement.getId().equals(game.getMatch().getId()));
            lineups.saved.removeIf(lineup -> replacement.getId().equals(lineup.getMatch().getId()));
            saved.set(index, replacement);
            lineups.saved.addAll(content.lineups());
            games.saved.addAll(content.games());
            setScores.saved.addAll(content.setScores());
            doublesPairs.saved.addAll(content.doublesPairs());
        }

        @Override
        public void updateSchedule(UUID matchId, MatchSchedule schedule) {
            int index = indexOfMatch(matchId);
            Match existing = saved.get(index);
            if (existing.getStatus() != MatchStatus.SCHEDULED) {
                throw new IllegalStateException(
                        "Only SCHEDULED matches can be rescheduled, match " + matchId + " is PLAYED");
            }
            saved.set(index, Match.builder()
                    .id(existing.getId())
                    .source(existing.getSource())
                    .externalId(existing.getExternalId())
                    .sourceFixtureId(existing.getSourceFixtureId())
                    .competition(existing.getCompetition())
                    .season(existing.getSeason())
                    .groupNumber(existing.getGroupNumber())
                    .round(existing.getRound())
                    .phase(existing.getPhase())
                    .dateTime(schedule.dateTime())
                    .city(schedule.city())
                    .venue(schedule.venue())
                    .homeTeam(existing.getHomeTeam())
                    .awayTeam(existing.getAwayTeam())
                    .refereeName(schedule.refereeName())
                    .refereeLicense(schedule.refereeLicense())
                    .protested(existing.isProtested())
                    .status(existing.getStatus())
                    .createExisting());
        }

        private int indexOfMatch(UUID matchId) {
            for (int i = 0; i < saved.size(); i++) {
                if (matchId.equals(saved.get(i).getId())) {
                    return i;
                }
            }
            throw new IllegalStateException("Match not found: " + matchId);
        }

        /**
         * FEAT-00084: groups the stored matches of one source and season exactly as the JPA adapter
         * groups its rows and delegates the definition to {@link RoundProgressCalculator}, so the
         * in-memory and persisted rules cannot drift apart.
         */
        @Override
        public List<RoundProgress> findRoundProgress(ImportSource source, Season season) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(season, "season");
            Map<RoundRowKey, Long> countsByRow = new LinkedHashMap<>();
            for (Match match : saved) {
                if (!source.equals(match.getSource()) || !season.equals(match.getSeason())) {
                    continue;
                }
                countsByRow.merge(new RoundRowKey(match.getCompetition(), match.getGroupNumber(),
                        match.getPhase(), match.getRound(), match.getStatus()), 1L, Long::sum);
            }
            List<RoundStatusCount> counts = countsByRow.entrySet().stream()
                    .map(entry -> new RoundStatusCount(entry.getKey().competition(), entry.getKey().groupNumber(),
                            entry.getKey().phase(), entry.getKey().round(), entry.getKey().status(),
                            entry.getValue()))
                    .toList();
            return RoundProgressCalculator.compute(source, season, counts);
        }

        private record RoundRowKey(String competition, Integer groupNumber, String phase, int round,
                                   MatchStatus status) {
        }

        /** FEAT-00086: source-scoped read of every stored match with one status. */
        @Override
        public List<Match> findMatchesBySourceSeasonAndStatus(ImportSource source, Season season,
                                                              MatchStatus status) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(season, "season");
            Objects.requireNonNull(status, "status");
            return saved.stream()
                    .filter(m -> source.equals(m.getSource()))
                    .filter(m -> season.equals(m.getSeason()))
                    .filter(m -> status == m.getStatus())
                    .toList();
        }
    }

    public static final class Lineups implements LineupRepository {
        public final List<Lineup> saved = new ArrayList<>();
        private final PlayerSeasons playerSeasons;

        Lineups() {
            this(null);
        }

        public Lineups(PlayerSeasons playerSeasons) {
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

    public static final class Games implements GameRepository {
        public final List<Game> saved = new ArrayList<>();

        @Override
        public List<Game> findGamesByMatchId(UUID matchId) {
            return saved.stream().filter(g -> matchId.equals(g.getMatch().getId())).toList();
        }

        @Override
        public List<Game> findGamesByMatchIds(java.util.Collection<UUID> matchIds) {
            return saved.stream().filter(g -> matchIds.contains(g.getMatch().getId())).toList();
        }

        @Override
        public void saveGames(List<Game> games) {
            saved.addAll(games);
        }
    }

    public static final class SetScores implements SetScoreRepository {
        public final List<SetScore> saved = new ArrayList<>();

        @Override
        public void saveSetScores(List<SetScore> setScores) {
            saved.addAll(setScores);
        }
    }

    public static final class DoublesPairs implements DoublesPairRepository {
        public final List<DoublesPair> saved = new ArrayList<>();

        @Override
        public List<DoublesPair> findDoublesPairsByGameIds(java.util.Collection<UUID> gameIds) {
            return saved.stream().filter(pair -> gameIds.contains(pair.getGame().getId())).toList();
        }

        @Override
        public void saveDoublesPairs(List<DoublesPair> doublesPairs) {
            saved.addAll(doublesPairs);
        }
    }

    static final class ScheduledMatchBackfill implements ScheduledMatchBackfillRepository {
        private final Matches matches;
        private final Games games;
        private final Lineups lineups;
        private final SetScores setScores;
        private final DoublesPairs doublesPairs;

        ScheduledMatchBackfill(Matches matches, Games games, Lineups lineups, SetScores setScores,
                               DoublesPairs doublesPairs) {
            this.matches = matches;
            this.games = games;
            this.lineups = lineups;
            this.setScores = setScores;
            this.doublesPairs = doublesPairs;
        }

        @Override
        public List<ScheduledMatchBackfillCandidate> findScheduledBackfillCandidates(ImportSource source, Season season) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(season, "season");
            return matches.saved.stream()
                    .filter(match -> source.equals(match.getSource()) && season.equals(match.getSeason()))
                    .filter(this::isCandidate)
                    .sorted(Comparator
                            .comparing(Match::getCompetition, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(match -> Optional.ofNullable(match.getGroupNumber()).orElse(Integer.MIN_VALUE))
                            .thenComparingInt(Match::getRound)
                            .thenComparing(Match::getId))
                    .map(this::toCandidate)
                    .toList();
        }

        @Override
        public ScheduledMatchBackfillWriteResult markScheduled(ImportSource source, Season season, Collection<UUID> matchIds) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(season, "season");
            if (matchIds == null || matchIds.isEmpty()) {
                return new ScheduledMatchBackfillWriteResult(0, 0, 0, 0, 0);
            }
            Set<UUID> candidateIds = findScheduledBackfillCandidates(source, season).stream()
                    .map(ScheduledMatchBackfillCandidate::matchId)
                    .collect(Collectors.toSet());
            List<UUID> offending = matchIds.stream().filter(id -> !candidateIds.contains(id)).toList();
            if (!offending.isEmpty()) {
                throw new IllegalStateException(
                        "Match ids are no longer backfill candidates for " + source + "/" + season + ": " + offending);
            }

            int doublesPairsDeleted = 0;
            int setScoresDeleted = 0;
            int gamesDeleted = 0;
            int lineupsDeleted = 0;
            int matchesUpdated = 0;

            for (UUID matchId : matchIds) {
                List<UUID> gameIds = games.saved.stream()
                        .filter(game -> matchId.equals(game.getMatch().getId()))
                        .map(Game::getId)
                        .toList();

                doublesPairsDeleted += removeIf(doublesPairs.saved, pair -> gameIds.contains(pair.getGame().getId()));
                setScoresDeleted += removeIf(setScores.saved, setScore -> gameIds.contains(setScore.getGame().getId()));
                gamesDeleted += removeIf(games.saved, game -> matchId.equals(game.getMatch().getId()));
                lineupsDeleted += removeIf(lineups.saved, lineup -> matchId.equals(lineup.getMatch().getId()));

                Match existing = matches.saved.stream()
                        .filter(match -> matchId.equals(match.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Match not found: " + matchId));
                Match updated = Match.builder()
                        .id(existing.getId())
                        .source(existing.getSource())
                        .externalId(existing.getExternalId())
                        .sourceFixtureId(existing.getSourceFixtureId())
                        .competition(existing.getCompetition())
                        .season(existing.getSeason())
                        .groupNumber(existing.getGroupNumber())
                        .round(existing.getRound())
                        .phase(existing.getPhase())
                        .dateTime(existing.getDateTime())
                        .city(existing.getCity())
                        .venue(existing.getVenue())
                        .homeTeam(existing.getHomeTeam())
                        .awayTeam(existing.getAwayTeam())
                        .refereeName(existing.getRefereeName())
                        .refereeLicense(existing.getRefereeLicense())
                        .protested(existing.isProtested())
                        .status(MatchStatus.SCHEDULED)
                        .createExisting();
                matches.saved.removeIf(match -> matchId.equals(match.getId()));
                matches.saved.add(updated);
                matchesUpdated++;
            }

            return new ScheduledMatchBackfillWriteResult(
                    matchesUpdated, gamesDeleted, lineupsDeleted, setScoresDeleted, doublesPairsDeleted);
        }

        private boolean isCandidate(Match match) {
            if (match.getStatus() != MatchStatus.PLAYED) {
                return false;
            }
            if (match.getWinnerTeam() != null) {
                return false;
            }
            if (nonZero(match.getHomeGamesWon()) || nonZero(match.getAwayGamesWon())) {
                return false;
            }
            if (nonZero(match.getHomeSetsWon()) || nonZero(match.getAwaySetsWon())) {
                return false;
            }
            List<Game> matchGames = games.saved.stream()
                    .filter(game -> match.getId().equals(game.getMatch().getId()))
                    .toList();
            for (Game game : matchGames) {
                if (game.getWinnerSide() != null) {
                    return false;
                }
                if (nonZero(game.getHomeSetsWon()) || nonZero(game.getAwaySetsWon())) {
                    return false;
                }
                boolean hasSetScore = setScores.saved.stream()
                        .anyMatch(setScore -> game.getId().equals(setScore.getGame().getId()));
                if (hasSetScore) {
                    return false;
                }
            }
            return true;
        }

        private ScheduledMatchBackfillCandidate toCandidate(Match match) {
            int gameCount = (int) games.saved.stream()
                    .filter(game -> match.getId().equals(game.getMatch().getId()))
                    .count();
            int lineupCount = (int) lineups.saved.stream()
                    .filter(lineup -> match.getId().equals(lineup.getMatch().getId()))
                    .count();
            return new ScheduledMatchBackfillCandidate(
                    match.getId(),
                    match.getCompetition(),
                    match.getGroupNumber(),
                    match.getRound(),
                    match.getPhase(),
                    match.getDateTime() == null ? null : match.getDateTime().toLocalDate(),
                    match.getHomeTeam().getId(),
                    match.getAwayTeam().getId(),
                    gameCount,
                    lineupCount);
        }

        private static boolean nonZero(Integer value) {
            return value != null && value != 0;
        }

        private static <T> int removeIf(List<T> list, Predicate<T> predicate) {
            int before = list.size();
            list.removeIf(predicate);
            return before - list.size();
        }
    }
}
