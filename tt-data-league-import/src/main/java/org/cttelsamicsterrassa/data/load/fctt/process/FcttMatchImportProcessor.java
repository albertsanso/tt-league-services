package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
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
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaSet;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores an FCTT report's match, lineups, games, set scores, and doubles-pair members.
 *
 * <p>The match natural key comes from the directory competition and group, the payload round, and
 * the two source-scoped club seasons. An existing match is deliberately left unchanged so a
 * traversal can be safely re-run.</p>
 */
@Component
@Order(FcttMatchImportProcessor.ORDER)
public class FcttMatchImportProcessor implements FcttMatchReportProcessor {

    /** Matches run last because they reference clubs and player registrations. */
    public static final int ORDER = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttMatchImportProcessor.class);
    private static final String GAME_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private static final String GAME_TYPE_DOUBLES = "DOUBLES";
    private static final String SIDE_HOME = "HOME";
    private static final String SIDE_AWAY = "AWAY";
    private static final Map<String, Integer> POSITION_BY_LETTER = Map.of(
            "A", 1, "B", 2, "C", 3,
            "X", 1, "Y", 2, "Z", 3);

    private final ClubRepository clubRepository;
    private final ClubSeasonRepository clubSeasonRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final MatchRepository matchRepository;
    private final LineupRepository lineupRepository;
    private final GameRepository gameRepository;
    private final SetScoreRepository setScoreRepository;
    private final DoublesPairRepository doublesPairRepository;

    public FcttMatchImportProcessor(ClubRepository clubRepository,
                                    ClubSeasonRepository clubSeasonRepository,
                                    PlayerSeasonRepository playerSeasonRepository,
                                    MatchRepository matchRepository,
                                    LineupRepository lineupRepository,
                                    GameRepository gameRepository,
                                    SetScoreRepository setScoreRepository,
                                    DoublesPairRepository doublesPairRepository) {
        this.clubRepository = clubRepository;
        this.clubSeasonRepository = clubSeasonRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.gameRepository = gameRepository;
        this.setScoreRepository = setScoreRepository;
        this.doublesPairRepository = doublesPairRepository;
    }

    @Override
    public void process(FcttMatchReportContext context) {
        if (context.acta().teams() == null || context.acta().teams().home() == null
                || context.acta().teams().away() == null) {
            LOGGER.warn("FCTT report {} has incomplete teams; match not stored", context.matchReportFile());
            return;
        }
        if (context.groupNumber().isEmpty()) {
            LOGGER.warn("FCTT report {} has invalid group folder {}; match not stored",
                    context.matchReportFile(), context.group());
            return;
        }

        Season season = context.toSeason();
        Optional<ClubSeason> homeClub = resolveClubSeason(context.acta().teams().home(), season, context);
        Optional<ClubSeason> awayClub = resolveClubSeason(context.acta().teams().away(), season, context);
        if (homeClub.isEmpty() || awayClub.isEmpty()) {
            return;
        }

        int groupNumber = context.groupNumber().orElseThrow();
        if (matchRepository.findMatchByNaturalKey(context.competition(), season, groupNumber, context.round(),
                homeClub.get().getId(), awayClub.get().getId()).isPresent()) {
            LOGGER.debug("FCTT match already stored for {}; skipping", context.matchReportFile());
            return;
        }

        Match match = buildMatch(context, season, groupNumber, homeClub.get(), awayClub.get());
        matchRepository.saveMatch(match);

        SideLineup home = resolveLineup(context.acta().lineups() == null
                ? Map.of() : context.acta().lineups().home(), season, context);
        SideLineup away = resolveLineup(context.acta().lineups() == null
                ? Map.of() : context.acta().lineups().away(), season, context);
        lineupRepository.saveLineups(buildLineups(match, homeClub.get(), home, awayClub.get(), away));
        storeGames(context, match, home, away);
    }

    private Match buildMatch(FcttMatchReportContext context, Season season, int groupNumber,
                             ClubSeason homeClub, ClubSeason awayClub) {
        Acta acta = context.acta();
        ActaScore gamesWon = acta.finalResult() == null ? null : acta.finalResult().gamesWon();
        ActaScore setsWon = acta.finalResult() == null ? null : acta.finalResult().setsWon();
        return Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.FCTT)
                .competition(context.competition())
                .season(season)
                .groupNumber(groupNumber)
                .round(context.round())
                .dateTime(toDateTime(acta))
                .city(acta.venue() == null ? null : acta.venue().city())
                .venue(acta.venue() == null ? null : acta.venue().venue())
                .homeClub(homeClub)
                .awayClub(awayClub)
                .winnerClub(resolveWinnerClub(acta, homeClub, awayClub, context))
                .refereeName(refereeName(acta))
                .homeGamesWon(gamesWon == null ? null : gamesWon.home())
                .awayGamesWon(gamesWon == null ? null : gamesWon.away())
                .homeSetsWon(setsWon == null ? null : setsWon.home())
                .awaySetsWon(setsWon == null ? null : setsWon.away())
                .protested(acta.wasProtested())
                .build();
    }

    private static ZonedDateTime toDateTime(Acta acta) {
        if (acta.date() == null) {
            return null;
        }
        return acta.date().atTime(acta.time() == null ? LocalTime.MIDNIGHT : acta.time())
                .atZone(Match.COMPETITION_ZONE);
    }

    private static String refereeName(Acta acta) {
        return acta.officials() == null || acta.officials().head() == null
                ? null : acta.officials().head().name();
    }

    private ClubSeason resolveWinnerClub(Acta acta, ClubSeason homeClub, ClubSeason awayClub,
                                         FcttMatchReportContext context) {
        if (acta.finalResult() == null) {
            return null;
        }
        String winnerName = acta.finalResult().winnerName();
        if (winnerName != null) {
            if (winnerName.equals(acta.teams().home().name())) {
                return homeClub;
            }
            if (winnerName.equals(acta.teams().away().name())) {
                return awayClub;
            }
            LOGGER.debug("FCTT winner \"{}\" matches neither team in {}; falling back to score",
                    winnerName, context.matchReportFile());
        }
        ActaScore gamesWon = acta.finalResult().gamesWon();
        if (gamesWon == null || gamesWon.home() == null || gamesWon.away() == null) {
            return null;
        }
        return gamesWon.home() > gamesWon.away() ? homeClub
                : gamesWon.away() > gamesWon.home() ? awayClub : null;
    }

    private SideLineup resolveLineup(Map<String, ActaLineupPlayer> letters, Season season,
                                     FcttMatchReportContext context) {
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();
        Map<String, Double> rankingByLetter = new LinkedHashMap<>();
        letters.forEach((letter, player) -> {
            if (player == null || player.license() == null) {
                LOGGER.warn("FCTT lineup letter {} has no licence in {}", letter, context.matchReportFile());
                return;
            }
            playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.FCTT, player.license(), season)
                    .ifPresentOrElse(playerSeason -> {
                        byLetter.put(letter, playerSeason);
                        rankingByLetter.put(letter, player.ranking());
                        if (player.name() != null) {
                            byName.put(player.name(), playerSeason);
                        }
                    }, () -> LOGGER.warn("No FCTT registration for licence {} in {}; lineup {} left out",
                            player.license(), season, letter));
        });
        return new SideLineup(byLetter, byName, rankingByLetter);
    }

    private List<Lineup> buildLineups(Match match, ClubSeason homeClub, SideLineup home,
                                      ClubSeason awayClub, SideLineup away) {
        List<Lineup> lineups = new ArrayList<>();
        addLineups(lineups, match, homeClub, home);
        addLineups(lineups, match, awayClub, away);
        return lineups;
    }

    private void addLineups(List<Lineup> lineups, Match match, ClubSeason clubSeason, SideLineup side) {
        side.byLetter().forEach((letter, player) -> {
            Integer position = POSITION_BY_LETTER.get(letter);
            if (position == null) {
                LOGGER.warn("Unknown FCTT lineup letter {} in match {}; entry left out", letter, match.getId());
                return;
            }
            Double ranking = side.rankingByLetter().get(letter);
            lineups.add(Lineup.builder()
                    .id(UUID.randomUUID())
                    .source(ImportSource.FCTT)
                    .match(match)
                    .clubSeason(clubSeason)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(ranking == null ? null : ranking.floatValue())
                    .build());
        });
    }

    private void storeGames(FcttMatchReportContext context, Match match, SideLineup home, SideLineup away) {
        List<Game> games = new ArrayList<>();
        List<SetScore> setScores = new ArrayList<>();
        List<DoublesPair> doublesPairs = new ArrayList<>();
        for (ActaGame actaGame : context.acta().games()) {
            if (actaGame.number() == null) {
                LOGGER.warn("FCTT game without a number in {}; left out", context.matchReportFile());
                continue;
            }
            Game game = buildGame(actaGame, match, home, away);
            games.add(game);
            setScores.addAll(buildSetScores(actaGame, game));
            if (actaGame.isDoubles()) {
                doublesPairs.addAll(buildDoublesPairs(actaGame, game, home, away, context));
            }
        }
        gameRepository.saveGames(games);
        setScoreRepository.saveSetScores(setScores);
        doublesPairRepository.saveDoublesPairs(doublesPairs);
    }

    private Game buildGame(ActaGame actaGame, Match match, SideLineup home, SideLineup away) {
        boolean doubles = actaGame.isDoubles();
        ActaScore setsWon = actaGame.setsWon();
        ActaScore cumulative = actaGame.cumulativeScore();
        String winnerSide = toSide(actaGame.winner());
        PlayerSeason homePlayer = doubles ? null : playerOf(actaGame.home(), home);
        PlayerSeason awayPlayer = doubles ? null : playerOf(actaGame.away(), away);
        return Game.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.FCTT)
                .match(match)
                .gameNumber(actaGame.number())
                .type(doubles ? GAME_TYPE_DOUBLES : GAME_TYPE_INDIVIDUAL)
                .crossover(actaGame.crossover() == null ? "" : actaGame.crossover())
                .homePlayer(homePlayer)
                .awayPlayer(awayPlayer)
                .homeSetsWon(setsWon == null ? null : setsWon.home())
                .awaySetsWon(setsWon == null ? null : setsWon.away())
                .winner(winnerOf(winnerSide, homePlayer, awayPlayer))
                .winnerSide(winnerSide)
                .cumulativeHomeSetsWon(cumulative == null || cumulative.home() == null ? 0 : cumulative.home())
                .cumulativeAwaySetsWon(cumulative == null || cumulative.away() == null ? 0 : cumulative.away())
                .notPlayed(actaGame.wasNotPlayed())
                .reason(actaGame.reason())
                .build();
    }

    private static String toSide(String winner) {
        return ActaGame.WINNER_HOME.equals(winner) ? SIDE_HOME
                : ActaGame.WINNER_AWAY.equals(winner) ? SIDE_AWAY : null;
    }

    private static PlayerSeason winnerOf(String winnerSide, PlayerSeason homePlayer, PlayerSeason awayPlayer) {
        return SIDE_HOME.equals(winnerSide) ? homePlayer : SIDE_AWAY.equals(winnerSide) ? awayPlayer : null;
    }

    private static PlayerSeason playerOf(ActaParticipant participant, SideLineup side) {
        return participant == null || participant.letter() == null ? null : side.byLetter().get(participant.letter());
    }

    private static List<SetScore> buildSetScores(ActaGame actaGame, Game game) {
        List<SetScore> scores = new ArrayList<>();
        for (ActaSet set : actaGame.sets()) {
            if (set.number() != null && set.homePoints() != null && set.awayPoints() != null) {
                scores.add(SetScore.builder()
                        .id(UUID.randomUUID())
                        .source(ImportSource.FCTT)
                        .game(game)
                        .setNumber(set.number())
                        .homePoints(set.homePoints())
                        .awayPoints(set.awayPoints())
                        .build());
            }
        }
        return scores;
    }

    private List<DoublesPair> buildDoublesPairs(ActaGame actaGame, Game game, SideLineup home,
                                                SideLineup away, FcttMatchReportContext context) {
        List<DoublesPair> pairs = new ArrayList<>();
        addDoublesPair(pairs, game, SIDE_HOME, actaGame.home(), home, context);
        addDoublesPair(pairs, game, SIDE_AWAY, actaGame.away(), away, context);
        return pairs;
    }

    private void addDoublesPair(List<DoublesPair> pairs, Game game, String side, ActaParticipant participant,
                                SideLineup lineup, FcttMatchReportContext context) {
        if (participant == null) {
            return;
        }
        for (ActaLineupPlayer player : participant.doublesPlayers()) {
            PlayerSeason playerSeason = playerOf(player, lineup, context.toSeason());
            if (playerSeason == null) {
                LOGGER.warn("FCTT doubles player \"{}\" with licence {} is unavailable in {}; pair member left out",
                        player == null ? null : player.name(), player == null ? null : player.license(),
                        context.matchReportFile());
                continue;
            }
            pairs.add(DoublesPair.builder()
                    .id(UUID.randomUUID())
                    .source(ImportSource.FCTT)
                    .game(game)
                    .side(side)
                    .player(playerSeason)
                    .build());
        }
    }

    private PlayerSeason playerOf(ActaLineupPlayer player, SideLineup lineup, Season season) {
        if (player == null || player.name() == null || player.license() == null) {
            return null;
        }
        PlayerSeason inLineup = lineup.byName().get(player.name());
        if (inLineup != null && player.license().equals(inLineup.getLicense())) {
            return inLineup;
        }
        return playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.FCTT, player.license(), season)
                .orElse(null);
    }

    private Optional<ClubSeason> resolveClubSeason(ActaTeam team, Season season, FcttMatchReportContext context) {
        if (team.name() == null || team.name().isBlank()) {
            LOGGER.warn("FCTT report {} has a team without a name; match not stored", context.matchReportFile());
            return Optional.empty();
        }
        Optional<Club> club = clubRepository.findClubBySourceAndName(ImportSource.FCTT, team.name());
        if (club.isEmpty()) {
            LOGGER.warn("No FCTT club named {} for {}; match not stored", team.name(), context.matchReportFile());
            return Optional.empty();
        }
        Optional<ClubSeason> clubSeason = clubSeasonRepository.findClubSeasonByClubAndSeason(club.get().getId(), season);
        if (clubSeason.isEmpty()) {
            LOGGER.warn("FCTT club {} has no entry for season {}; match not stored", team.name(), season);
        }
        return clubSeason;
    }

    private record SideLineup(Map<String, PlayerSeason> byLetter,
                              Map<String, PlayerSeason> byName,
                              Map<String, Double> rankingByLetter) {
    }
}
