package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-ins for the persistence ports, enforcing the same natural keys as the schema so
 * that the idempotency the processors rely on is actually exercised.
 */
final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    static final class Clubs implements ClubRepository {
        final Map<UUID, Club> byId = new LinkedHashMap<>();

        @Override
        public Optional<Club> findClubById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Club> findClubByName(String name) {
            return byId.values().stream().filter(club -> Objects.equals(club.getName(), name)).findFirst();
        }

        @Override
        public Optional<Club> findClubBySourceAndName(ImportSource source, String name) {
            return byId.values().stream()
                    .filter(club -> Objects.equals(club.getSource(), source) && Objects.equals(club.getName(), name))
                    .findFirst();
        }

        @Override
        public void saveClub(Club club) {
            byId.put(club.getId(), club);
        }

        @Override
        public void deleteClubById(UUID id) {
            byId.remove(id);
        }

        @Override
        public List<Club> findAllClubsBySimilarName(String name) {
            return byId.values().stream()
                    .filter(club -> club.getName() != null && club.getName().contains(name))
                    .toList();
        }

        @Override
        public List<Club> findAllClubsBySimilarNameAndSource(String name, String source) {
            return byId.values().stream()
                    .filter(club -> club.getSource() != null && club.getSource().toString().equals(source))
                    .filter(club -> club.getName() != null && club.getName().contains(name))
                    .toList();
        }


    }

    static final class ClubSeasons implements ClubSeasonRepository {
        final Map<UUID, ClubSeason> byId = new LinkedHashMap<>();

        @Override
        public Optional<ClubSeason> findClubSeasonById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ClubSeason> findClubSeasonByNameAndSeasonAndSource(String name, Season season, ImportSource source) {
            return Optional.empty();
        }

        @Override
        public Optional<ClubSeason> findClubSeasonByClubAndSeason(UUID clubId, Season season) {
            return byId.values().stream()
                    .filter(cs -> cs.getClub() != null && clubId.equals(cs.getClub().getId()))
                    .filter(cs -> season.equals(cs.getSeason()))
                    .findFirst();
        }

        @Override
        public Optional<ClubSeason> findClubSeasonByClubAndSeasonAndSource(UUID clubId, Season season, String source) {
            return Optional.empty();
        }

        @Override
        public void saveClubSeason(ClubSeason clubSeason) {
            byId.put(clubSeason.getId(), clubSeason);
        }

        @Override
        public void deleteClubSeasonById(UUID id) {
            byId.remove(id);
        }

        @Override
        public List<ClubSeason> findAllClubSeasonsBySimilarName(String name) {
            return byId.values().stream()
                    .filter(cs -> cs.getName() != null && cs.getName().contains(name))
                    .toList();
        }

        @Override
        public List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeason(String name, Season season) {
            return findAllClubSeasonsBySimilarName(name).stream()
                    .filter(cs -> season.equals(cs.getSeason()))
                    .toList();
        }

        @Override
        public List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeasonAndSoure(String name, Season season, String source) {
            return List.of();
        }
    }

    static final class Players implements PlayerRepository {
        final Map<UUID, Player> byId = new LinkedHashMap<>();

        @Override
        public Optional<Player> findPlayerById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Player> findPlayerByName(String name) {
            return byId.values().stream().filter(p -> Objects.equals(p.getName(), name)).findFirst();
        }

        @Override
        public Optional<Player> findPlayerBySourceAndName(ImportSource source, String name) {
            return byId.values().stream()
                    .filter(p -> Objects.equals(p.getSource(), source) && Objects.equals(p.getName(), name))
                    .findFirst();
        }

        @Override
        public void savePlayer(Player player) {
            byId.put(player.getId(), player);
        }
    }

    static final class PlayerSeasons implements PlayerSeasonRepository {
        final Map<UUID, PlayerSeason> byId = new LinkedHashMap<>();

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
        public void savePlayerSeason(PlayerSeason playerSeason) {
            byId.put(playerSeason.getId(), playerSeason);
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
                                                     UUID homeClubSeasonId,
                                                     UUID awayClubSeasonId) {
            return saved.stream()
                    .filter(m -> competition.equals(m.getCompetition()))
                    .filter(m -> season.equals(m.getSeason()))
                    .filter(m -> m.getGroupNumber() == groupNumber && m.getRound() == round)
                    .filter(m -> homeClubSeasonId.equals(m.getHomeClub().getId()))
                    .filter(m -> awayClubSeasonId.equals(m.getAwayClub().getId()))
                    .findFirst();
        }

        @Override
        public void saveMatch(Match match) {
            saved.add(match);
        }
    }

    static final class Lineups implements LineupRepository {
        final List<Lineup> saved = new ArrayList<>();

        @Override
        public List<Lineup> findLineupsByMatchId(UUID matchId) {
            return saved.stream().filter(l -> matchId.equals(l.getMatch().getId())).toList();
        }

        @Override
        public void saveLineups(List<Lineup> lineups) {
            saved.addAll(lineups);
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
