package org.cttelsamicsterrassa.data.load.rfetm.process;

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
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.load.shared.parse.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaSet;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportProcessor;
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
 * Stores the match aggregate of a report: the match itself, its lineups, its games, the set scores
 * of each game and the members of each doubles pair.
 *
 * <p>Runs after {@link RfetmClubImportProcessor} and {@link RfetmPlayerImportProcessor}, whose rows it looks
 * up rather than creates. It is idempotent through the match natural key: if the match is already
 * stored, the report is left alone, so re-running a season neither duplicates nor rewrites.</p>
 *
 * <h2>Identity decisions</h2>
 * <ul>
 *   <li>Competition comes from the directory path, not from the payload's generic
 *       {@code competicion} field.</li>
 *   <li>Singles players are resolved through the lineup letter recorded on the game.</li>
 *   <li>Doubles players are resolved by exact name and licence against this match's own lineup.
 *       There is deliberately no fallback to a global name search: across tens of thousands of
 *       matches a name collision is close to certain, and a wrong licence is worse than a missing
 *       row. Unresolved players are logged and the pair member is left out.</li>
 * </ul>
 */
@Component
@Order(RfetmMatchImportProcessor.ORDER)
public class RfetmMatchImportProcessor implements MatchReportProcessor {

    /** Matches come last: they reference clubs and players. */
    public static final int ORDER = 30;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmMatchImportProcessor.class);

    private static final String GAME_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private static final String GAME_TYPE_DOUBLES = "DOUBLES";
    private static final String SIDE_HOME = "HOME";
    private static final String SIDE_AWAY = "AWAY";

    /** Lineup letter to the position it stands for within a team. */
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

    public RfetmMatchImportProcessor(ClubRepository clubRepository,
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
    public void process(MatchReportContext context) {
        Acta acta = context.acta();
        if (acta == null) {
            LOGGER.warn("No payload for {}; nothing to store", context.matchReportFile());
            return;
        }

        Season season = context.toSeason();
        Optional<ClubSeason> homeClub = resolveClubSeason(context.homeClub(), season, context);
        Optional<ClubSeason> awayClub = resolveClubSeason(context.awayClub(), season, context);
        if (homeClub.isEmpty() || awayClub.isEmpty()) {
            return;
        }

        String competition = context.competition();
        int groupNumber = acta.group() != null ? acta.group() : 0;
        int round = context.round();

        if (matchRepository.findMatchByNaturalKey(competition, season, groupNumber, round,
                homeClub.get().getId(), awayClub.get().getId()).isPresent()) {
            LOGGER.debug("Match already stored for {}; skipping", context.matchReportFile());
            return;
        }

        Match match = buildMatch(context, acta, season, competition, groupNumber, round,
                homeClub.get(), awayClub.get());
        matchRepository.saveMatch(match);

        SideLineup home = resolveLineup(acta.lineups() != null ? acta.lineups().home() : Map.of(), season, context);
        SideLineup away = resolveLineup(acta.lineups() != null ? acta.lineups().away() : Map.of(), season, context);

        lineupRepository.saveLineups(buildLineups(match, homeClub.get(), home, awayClub.get(), away));
        storeGames(context, acta, match, home, away);
    }

    // --- match -----------------------------------------------------------------------------

    private Match buildMatch(MatchReportContext context,
                             Acta acta,
                             Season season,
                             String competition,
                             int groupNumber,
                             int round,
                             ClubSeason homeClub,
                             ClubSeason awayClub) {
        ActaScore gamesWon = acta.finalResult() != null ? acta.finalResult().gamesWon() : null;
        ActaScore setsWon = acta.finalResult() != null ? acta.finalResult().setsWon() : null;

        return Match.builder()
                .id(UUID.randomUUID())
                .source(ImportSource.RFETM)
                .competition(competition)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .dateTime(toDateTime(acta))
                .city(acta.venue() != null ? acta.venue().city() : null)
                .venue(acta.venue() != null ? acta.venue().venue() : null)
                .homeClub(homeClub)
                .awayClub(awayClub)
                .winnerClub(resolveWinnerClub(acta, homeClub, awayClub, context))
                .refereeName(refereeName(acta))
                .homeGamesWon(gamesWon != null ? gamesWon.home() : null)
                .awayGamesWon(gamesWon != null ? gamesWon.away() : null)
                .homeSetsWon(setsWon != null ? setsWon.home() : null)
                .awaySetsWon(setsWon != null ? setsWon.away() : null)
                .protested(acta.wasProtested())
                .createNew();
    }

    private static ZonedDateTime toDateTime(Acta acta) {
        if (acta.date() == null) {
            return null;
        }
        LocalTime time = acta.time() != null ? acta.time() : LocalTime.MIDNIGHT;
        return acta.date().atTime(time).atZone(Match.COMPETITION_ZONE);
    }

    private static String refereeName(Acta acta) {
        if (acta.officials() == null || acta.officials().head() == null) {
            return null;
        }
        return acta.officials().head().name();
    }

    /**
     * The payload names the winner by club name, which is ambiguous on its own. It is only trusted
     * when it matches one of this report's two team names; otherwise the games score decides, and a
     * draw leaves the winner unset.
     */
    private ClubSeason resolveWinnerClub(Acta acta,
                                         ClubSeason homeClub,
                                         ClubSeason awayClub,
                                         MatchReportContext context) {
        if (acta.finalResult() == null) {
            return null;
        }

        String winnerName = acta.finalResult().winnerName();
        if (winnerName != null && acta.teams() != null) {
            ActaTeam home = acta.teams().home();
            ActaTeam away = acta.teams().away();
            if (home != null && winnerName.equals(home.name())) {
                return homeClub;
            }
            if (away != null && winnerName.equals(away.name())) {
                return awayClub;
            }
            LOGGER.debug("Winner \"{}\" matches neither team in {}; falling back to the score",
                    winnerName, context.matchReportFile());
        }

        ActaScore gamesWon = acta.finalResult().gamesWon();
        if (gamesWon == null || gamesWon.home() == null || gamesWon.away() == null) {
            return null;
        }
        if (gamesWon.home() > gamesWon.away()) {
            return homeClub;
        }
        return gamesWon.away() > gamesWon.home() ? awayClub : null;
    }

    // --- lineups ---------------------------------------------------------------------------

    private SideLineup resolveLineup(Map<String, ActaLineupPlayer> letters,
                                     Season season,
                                     MatchReportContext context) {
        Map<String, PlayerSeason> byLetter = new LinkedHashMap<>();
        Map<String, PlayerSeason> byName = new LinkedHashMap<>();
        Map<String, Double> rankingByLetter = new LinkedHashMap<>();

        letters.forEach((letter, player) -> {
            if (player == null || player.license() == null) {
                LOGGER.warn("Lineup letter {} has no licence in {}", letter, context.matchReportFile());
                return;
            }
            Optional<PlayerSeason> playerSeason =
                    playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, player.license(), season);
            if (playerSeason.isEmpty()) {
                LOGGER.warn("No player registered for licence {} in {}; lineup letter {} left out",
                        player.license(), season, letter);
                return;
            }
            byLetter.put(letter, playerSeason.get());
            rankingByLetter.put(letter, player.ranking());
            if (player.name() != null) {
                byName.put(player.name(), playerSeason.get());
            }
        });

        return new SideLineup(byLetter, byName, rankingByLetter);
    }

    private List<Lineup> buildLineups(Match match,
                                      ClubSeason homeClub,
                                      SideLineup home,
                                      ClubSeason awayClub,
                                      SideLineup away) {
        List<Lineup> lineups = new ArrayList<>();
        addLineups(lineups, match, homeClub, home);
        addLineups(lineups, match, awayClub, away);
        return lineups;
    }

    private void addLineups(List<Lineup> lineups, Match match, ClubSeason clubSeason, SideLineup side) {
        side.byLetter().forEach((letter, player) -> {
            Integer position = POSITION_BY_LETTER.get(letter);
            if (position == null) {
                LOGGER.warn("Unknown lineup letter {} in match {}; entry left out", letter, match.getId());
                return;
            }
            Double ranking = side.rankingByLetter().get(letter);
            lineups.add(Lineup.builder()
                    .id(UUID.randomUUID())
                    .match(match)
                    .clubSeason(clubSeason)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(ranking != null ? ranking.floatValue() : null)
                    .createNew());
        });
    }

    // --- games -----------------------------------------------------------------------------

    private void storeGames(MatchReportContext context,
                            Acta acta,
                            Match match,
                            SideLineup home,
                            SideLineup away) {
        List<Game> games = new ArrayList<>();
        List<SetScore> setScores = new ArrayList<>();
        List<DoublesPair> doublesPairs = new ArrayList<>();

        for (ActaGame actaGame : acta.games()) {
            if (actaGame.number() == null) {
                LOGGER.warn("Game without a number in {}; left out", context.matchReportFile());
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
                .source(ImportSource.RFETM)
                .match(match)
                .gameNumber(actaGame.number())
                .type(doubles ? GAME_TYPE_DOUBLES : GAME_TYPE_INDIVIDUAL)
                .crossover(actaGame.crossover() != null ? actaGame.crossover() : "")
                .homePlayer(homePlayer)
                .awayPlayer(awayPlayer)
                .homeSetsWon(setsWon != null ? setsWon.home() : null)
                .awaySetsWon(setsWon != null ? setsWon.away() : null)
                .winner(winnerOf(winnerSide, homePlayer, awayPlayer))
                .winnerSide(winnerSide)
                .cumulativeHomeSetsWon(cumulative != null && cumulative.home() != null ? cumulative.home() : 0)
                .cumulativeAwaySetsWon(cumulative != null && cumulative.away() != null ? cumulative.away() : 0)
                .notPlayed(actaGame.wasNotPlayed())
                .reason(actaGame.reason())
                .createNew();
    }

    private static String toSide(String actaWinner) {
        if (ActaGame.WINNER_HOME.equals(actaWinner)) {
            return SIDE_HOME;
        }
        return ActaGame.WINNER_AWAY.equals(actaWinner) ? SIDE_AWAY : null;
    }

    private static PlayerSeason winnerOf(String winnerSide, PlayerSeason homePlayer, PlayerSeason awayPlayer) {
        if (SIDE_HOME.equals(winnerSide)) {
            return homePlayer;
        }
        return SIDE_AWAY.equals(winnerSide) ? awayPlayer : null;
    }

    private static PlayerSeason playerOf(ActaParticipant participant, SideLineup side) {
        if (participant == null || participant.letter() == null) {
            return null;
        }
        return side.byLetter().get(participant.letter());
    }

    private PlayerSeason playerOf(ActaLineupPlayer participant, SideLineup side, Season season) {
        if (participant == null || participant.name() == null || participant.license() == null) {
            return null;
        }
        PlayerSeason player = side.byName().get(participant.name());
        if (player != null && participant.license().equals(player.getLicense())) {
            return player;
        }
        return playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, participant.license(), season)
                .orElse(null);
    }

    private static List<SetScore> buildSetScores(ActaGame actaGame, Game game) {
        List<SetScore> setScores = new ArrayList<>();
        for (ActaSet actaSet : actaGame.sets()) {
            if (actaSet.number() == null || actaSet.homePoints() == null || actaSet.awayPoints() == null) {
                continue;
            }
            setScores.add(SetScore.builder()
                    .id(UUID.randomUUID())
                    .game(game)
                    .setNumber(actaSet.number())
                    .homePoints(actaSet.homePoints())
                    .awayPoints(actaSet.awayPoints())
                    .build());
        }
        return setScores;
    }

    private List<DoublesPair> buildDoublesPairs(ActaGame actaGame,
                                                Game game,
                                                SideLineup home,
                                                SideLineup away,
                                                MatchReportContext context) {
        List<DoublesPair> pairs = new ArrayList<>();
        addDoublesPair(pairs, game, SIDE_HOME, actaGame.home(), home, context);
        addDoublesPair(pairs, game, SIDE_AWAY, actaGame.away(), away, context);
        return pairs;
    }

    private void addDoublesPair(List<DoublesPair> pairs,
                                Game game,
                                String side,
                                ActaParticipant participant,
                                SideLineup lineup,
                                MatchReportContext context) {
        if (participant == null) {
            return;
        }
        for (ActaLineupPlayer doublesPlayer : participant.doublesPlayers()) {
            PlayerSeason player = playerOf(doublesPlayer, lineup, context.toSeason());
            if (player == null) {
                LOGGER.warn("Doubles player \"{}\" with licence {} is unavailable in {}; pair member left out",
                        doublesPlayer.name(), doublesPlayer.license(), side, context.matchReportFile());
                continue;
            }
            pairs.add(DoublesPair.builder()
                    .id(UUID.randomUUID())
                    .game(game)
                    .side(side)
                    .player(player)
                    .build());
        }
    }

    // --- clubs -----------------------------------------------------------------------------

    private Optional<ClubSeason> resolveClubSeason(RfetmClubKey key, Season season, MatchReportContext context) {
        /*
        Optional<Club> club = clubRepository.findClubBySourceAndName(ImportSource.RFETM, key.name());
        if (club.isEmpty()) {
            LOGGER.warn("No club for {} in {}; match not stored", key, context.matchReportFile());
            return Optional.empty();
        }*/

        Optional<ClubSeason> clubSeason = clubSeasonRepository.findClubSeasonByNameAndSeasonAndSource(key.name(), season, ImportSource.RFETM);
        if (clubSeason.isEmpty()) {
            LOGGER.warn("Club {} has no entry for season {}; {} not stored",
                    key, season, context.matchReportFile());
        }

        /*
        Optional<ClubSeason> clubSeason = clubSeasonRepository.findClubSeasonByClubAndSeasonAndSource(club.get().getId(), season, ImportSource.RFETM.name());
        if (clubSeason.isEmpty()) {
            LOGGER.warn("Club {} has no entry for season {}; {} not stored",
                    key, season, context.matchReportFile());
        }*/
        return clubSeason;
    }

    /**
     * One side's lineup, resolved to stored players: by lineup letter for singles and by both name
     * and licence for doubles pairs.
     */
    private record SideLineup(Map<String, PlayerSeason> byLetter,
                              Map<String, PlayerSeason> byName,
                              Map<String, Double> rankingByLetter) {
    }
}
