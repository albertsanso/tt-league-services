package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;
import org.cttelsamicsterrassa.data.core.domain.game.model.Game;
import org.cttelsamicsterrassa.data.core.domain.game.model.SetScore;
import org.cttelsamicsterrassa.data.core.domain.game.repository.DoublesPairRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.GameRepository;
import org.cttelsamicsterrassa.data.core.domain.game.repository.SetScoreRepository;
import org.cttelsamicsterrassa.data.core.domain.lineup.model.Lineup;
import org.cttelsamicsterrassa.data.core.domain.lineup.repository.LineupRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchFixtureIdentityGuard.IncomingFixture;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleAction;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleOutcome;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlan;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecyclePlanner;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleSource;
import org.cttelsamicsterrassa.data.load.shared.match.lifecycle.MatchLifecycleWriter;
import org.cttelsamicsterrassa.data.load.shared.preview.FixturePreview;
import org.cttelsamicsterrassa.data.load.shared.preview.PreviewChange;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaScore;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaSet;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
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

    private final TeamRepository teamRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final MatchRepository matchRepository;
    private final LineupRepository lineupRepository;
    private final GameRepository gameRepository;
    private final SetScoreRepository setScoreRepository;
    private final DoublesPairRepository doublesPairRepository;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();
    private final MatchLifecycleWriter lifecycleWriter;
    private final MatchFixtureIdentityGuard identityGuard;
    private final MatchLifecyclePlanner planner = new MatchLifecyclePlanner();

    public FcttMatchImportProcessor(TeamRepository teamRepository,
                                    PlayerSeasonRepository playerSeasonRepository,
                                    MatchRepository matchRepository,
                                    LineupRepository lineupRepository,
                                    GameRepository gameRepository,
                                    SetScoreRepository setScoreRepository,
                                    DoublesPairRepository doublesPairRepository) {
        this.teamRepository = teamRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.matchRepository = matchRepository;
        this.lineupRepository = lineupRepository;
        this.gameRepository = gameRepository;
        this.setScoreRepository = setScoreRepository;
        this.doublesPairRepository = doublesPairRepository;
        this.lifecycleWriter = new MatchLifecycleWriter(matchRepository, lineupRepository, gameRepository,
                setScoreRepository, doublesPairRepository);
        this.identityGuard = new MatchFixtureIdentityGuard(matchRepository);
    }

    @Override
    public void process(FcttMatchReportContext reportContext) {
        ResolvedFixture resolved = resolve(reportContext);
        FcttMatchReportContext context = resolved.context();
        if (resolved.notStored() != null) {
            logNotStored(resolved);
            return;
        }

        Optional<Team> homeTeam = resolved.homeTeam();
        Optional<Team> awayTeam = resolved.awayTeam();
        if (homeTeam.isEmpty() && isBlank(resolved.homeTeamName())) {
            LOGGER.warn("FCTT report {} has a team without a name; match not stored", context.matchReportFile());
        }
        if (awayTeam.isEmpty() && isBlank(resolved.awayTeamName())) {
            LOGGER.warn("FCTT report {} has a team without a name; match not stored", context.matchReportFile());
        }
        if (homeTeam.isEmpty() && !isBlank(resolved.homeTeamName())) {
            LOGGER.warn("FCTT club {} has no entry for season {}; match not stored",
                    resolved.homeTeamName(), resolved.season());
        }
        if (awayTeam.isEmpty() && !isBlank(resolved.awayTeamName())) {
            LOGGER.warn("FCTT club {} has no entry for season {}; match not stored",
                    resolved.awayTeamName(), resolved.season());
        }
        // FEAT-00086: the fixture is "seen" even when a team is unregistered or the identity guard
        // later rejects it, so snapshot reconciliation cannot report it as absent.
        context.runContext().recordSnapshotFixture(resolved.competition(), resolved.groupNumber(),
                resolved.phase(), resolved.round(), resolved.sourceFixtureId(),
                homeTeam.map(Team::getId).orElse(null), awayTeam.map(Team::getId).orElse(null));
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            return;
        }
        if (resolved.conflict().isPresent()) {
            recordOutcome(context, MatchLifecycleOutcome.FIXTURE_IDENTITY_CONFLICT, resolved.conflict().get());
            return;
        }
        MatchLifecycleOutcome outcome = lifecycleWriter.apply(resolved.classification(), resolved.existing(),
                new FcttLifecycleSource(context, resolved.season(), resolved.groupNumber(),
                        homeTeam.get(), awayTeam.get()), context.runContext().amendedActaMode(),
                context.matchReportFile());
        recordOutcome(context, outcome, resolved.classification().reason());
    }

    /**
     * FEAT-00088: the read-only projection of {@link #process} for the import preview. It shares the
     * resolution step (so the natural key and fixture id cannot diverge) and the lifecycle planner,
     * but never writes, never records a snapshot fixture and never records a match outcome.
     */
    public FixturePreview preview(FcttMatchReportContext reportContext) {
        ResolvedFixture resolved = resolve(reportContext);
        if (resolved.notStored() != null) {
            return fixturePreview(resolved, PreviewChange.NOT_STORED, null, false,
                    resolved.classification().unresolvedPendingFixture()
                            ? resolved.classification().reason() : resolved.notStored().reason(resolved));
        }
        if (isBlank(resolved.homeTeamName()) || isBlank(resolved.awayTeamName())) {
            return fixturePreview(resolved, PreviewChange.NOT_STORED, null, false, "team without a name");
        }
        if (resolved.homeTeam().isEmpty() || resolved.awayTeam().isEmpty()) {
            MatchLifecyclePlan plan = MatchLifecyclePlanner.planCreation(resolved.classification());
            return fixturePreview(resolved, PreviewChange.of(plan), null, true,
                    resolved.classification().reason());
        }
        if (resolved.conflict().isPresent()) {
            return fixturePreview(resolved, PreviewChange.IDENTITY_CONFLICT, null, false,
                    resolved.conflict().get());
        }
        MatchLifecyclePlan plan = planner.plan(resolved.classification(), resolved.existing(),
                new FcttLifecycleSource(resolved.context(), resolved.season(), resolved.groupNumber(),
                        resolved.homeTeam().get(), resolved.awayTeam().get()));
        Integer existingRound = plan.action() == MatchLifecycleAction.UPGRADE_TO_PLAYED
                ? resolved.existing().get().getRound()
                : null;
        return fixturePreview(resolved, PreviewChange.of(plan), existingRound, false,
                resolved.classification().reason());
    }

    /**
     * The shared read-only resolution of one FCTT report: orientation, classification, scope and the
     * early-exit, team-lookup, natural-key and identity-guard steps. {@code process} and
     * {@code preview} both build on it so the key and fixture-id rules cannot diverge.
     */
    private ResolvedFixture resolve(FcttMatchReportContext reportContext) {
        ActaClassification classification = classifier.classify(reportContext.acta());
        Acta acta = classification.isPlayed() ? FcttActaOrientation.toHomeAway(reportContext.acta())
                : reportContext.acta();
        FcttMatchReportContext context = new FcttMatchReportContext(reportContext.season(), reportContext.gender(),
                reportContext.leagueCompetition(), reportContext.group(), reportContext.round(),
                reportContext.matchReportFile(), acta, reportContext.runContext());
        Season season = context.toSeason();
        String competition = context.competition();
        Integer groupNumber = context.groupNumber().isPresent() ? context.groupNumber().getAsInt() : null;
        int round = context.round();
        String phase = context.phase();

        if (classification.unresolvedPendingFixture()) {
            return ResolvedFixture.notStored(context, acta, classification, season, competition, groupNumber,
                    round, phase, NotStored.UNRESOLVED_PENDING);
        }
        if (acta.teams() == null || acta.teams().home() == null || acta.teams().away() == null) {
            return ResolvedFixture.notStored(context, acta, classification, season, competition, groupNumber,
                    round, phase, NotStored.INCOMPLETE_TEAMS);
        }
        if (context.hasGroupFolder() && context.groupNumber().isEmpty()) {
            return ResolvedFixture.notStored(context, acta, classification, season, competition, groupNumber,
                    round, phase, NotStored.INVALID_GROUP_FOLDER);
        }

        String homeTeamName = acta.teams().home().name();
        String awayTeamName = acta.teams().away().name();
        Optional<Team> homeTeam = lookupTeam(homeTeamName, season);
        Optional<Team> awayTeam = lookupTeam(awayTeamName, season);
        Optional<Match> existing = Optional.empty();
        Optional<String> conflict = Optional.empty();
        if (homeTeam.isPresent() && awayTeam.isPresent()) {
            existing = matchRepository.findMatchByNaturalKey(competition, season, groupNumber, round, phase,
                    homeTeam.get().getId(), awayTeam.get().getId());
            conflict = identityGuard.conflict(
                    new IncomingFixture(ImportSource.FCTT, acta.matchId(), competition, season, groupNumber,
                            round, phase),
                    existing);
        }
        return new ResolvedFixture(context, acta, classification, season, competition, groupNumber, round,
                phase, acta.matchId(), homeTeamName, awayTeamName, homeTeam, awayTeam, existing, conflict, null);
    }

    private Optional<Team> lookupTeam(String name, Season season) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        return teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.FCTT);
    }

    private void logNotStored(ResolvedFixture resolved) {
        switch (resolved.notStored()) {
            case UNRESOLVED_PENDING -> LOGGER.warn("FCTT report {} is a pending fixture without teams; not stored",
                    resolved.context().matchReportFile());
            case INCOMPLETE_TEAMS -> LOGGER.warn("FCTT report {} has incomplete teams; match not stored",
                    resolved.context().matchReportFile());
            case INVALID_GROUP_FOLDER -> LOGGER.warn("FCTT report {} has invalid group folder {}; match not stored",
                    resolved.context().matchReportFile(), resolved.context().group());
        }
    }

    private FixturePreview fixturePreview(ResolvedFixture resolved, PreviewChange change, Integer existingRound,
                                          boolean teamsPendingRegistration, String reason) {
        return new FixturePreview(ImportSource.FCTT, resolved.competition(), resolved.groupNumber(),
                resolved.phase(), resolved.round(), resolved.sourceFixtureId(), resolved.homeTeamName(),
                resolved.awayTeamName(), resolved.classification(), change, existingRound,
                teamsPendingRegistration, reason, resolved.context().matchReportFile());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void recordOutcome(FcttMatchReportContext context, MatchLifecycleOutcome outcome, String reason) {
        String effectiveReason = outcome.amendmentReason() != null ? outcome.amendmentReason() : reason;
        context.runContext().recordMatchOutcome(outcome, getClass().getSimpleName(),
                context.matchReportFile(), effectiveReason);
        if (outcome.isReportable()) {
            LOGGER.warn("FCTT match lifecycle {} for {}: {}", outcome, context.matchReportFile(), effectiveReason);
        }
    }

    /** Why a FCTT report is skipped before any team lookup or snapshot record. */
    private enum NotStored {
        UNRESOLVED_PENDING, INCOMPLETE_TEAMS, INVALID_GROUP_FOLDER;

        private String reason(ResolvedFixture resolved) {
            return switch (this) {
                case UNRESOLVED_PENDING -> resolved.classification().reason();
                case INCOMPLETE_TEAMS -> "incomplete teams";
                case INVALID_GROUP_FOLDER -> "invalid group folder " + resolved.context().group();
            };
        }
    }

    /** The read-only result of resolving one FCTT report, shared by {@code process} and {@code preview}. */
    private record ResolvedFixture(
            FcttMatchReportContext context,
            Acta acta,
            ActaClassification classification,
            Season season,
            String competition,
            Integer groupNumber,
            int round,
            String phase,
            String sourceFixtureId,
            String homeTeamName,
            String awayTeamName,
            Optional<Team> homeTeam,
            Optional<Team> awayTeam,
            Optional<Match> existing,
            Optional<String> conflict,
            NotStored notStored) {

        private static ResolvedFixture notStored(FcttMatchReportContext context, Acta acta,
                ActaClassification classification, Season season, String competition, Integer groupNumber,
                int round, String phase, NotStored notStored) {
            return new ResolvedFixture(context, acta, classification, season, competition, groupNumber, round,
                    phase, acta.matchId(), null, null, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), notStored);
        }
    }

    /**
     * The FCTT callbacks of the shared match lifecycle. Orientation has already been applied when
     * the classification is PLAYED, so the scheduled header never reads {@code resultado_final}.
     */
    private final class FcttLifecycleSource implements MatchLifecycleSource {

        private final FcttMatchReportContext context;
        private final Season season;
        private final Integer groupNumber;
        private final Team homeTeam;
        private final Team awayTeam;

        private FcttLifecycleSource(FcttMatchReportContext context, Season season, Integer groupNumber,
                                    Team homeTeam, Team awayTeam) {
            this.context = context;
            this.season = season;
            this.groupNumber = groupNumber;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
        }

        @Override
        public Match buildScheduledMatch(UUID id) {
            Acta acta = context.acta();
            return Match.builder()
                    .id(id)
                    .source(ImportSource.FCTT)
                    .sourceFixtureId(acta.matchId())
                    .competition(context.competition())
                    .season(season)
                    .groupNumber(groupNumber)
                    .round(context.round())
                    .phase(context.phase())
                    .dateTime(toDateTime(acta))
                    .city(acta.venue() == null ? null : acta.venue().city())
                    .venue(acta.venue() == null ? null : acta.venue().venue())
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .refereeName(refereeName(acta))
                    .protested(acta.wasProtested())
                    .status(MatchStatus.SCHEDULED)
                    .createNew();
        }

        @Override
        public MatchContent buildPlayedContent(UUID id, boolean existing) {
            Match match = buildPlayedMatch(id, existing);
            SideLineup home = resolveLineup(context.acta().lineups() == null
                    ? Map.of() : context.acta().lineups().home(), season, context);
            SideLineup away = resolveLineup(context.acta().lineups() == null
                    ? Map.of() : context.acta().lineups().away(), season, context);
            List<Lineup> lineups = buildLineups(match, homeTeam, home, awayTeam, away);
            BuiltGames built = buildGames(context, match, home, away);
            return new MatchContent(match, lineups, built.games(), built.setScores(), built.doublesPairs());
        }

        private Match buildPlayedMatch(UUID id, boolean existing) {
            Acta acta = context.acta();
            ActaScore gamesWon = acta.finalResult() == null ? null : acta.finalResult().gamesWon();
            ActaScore setsWon = acta.finalResult() == null ? null : acta.finalResult().setsWon();
            Match.MatchBuilder builder = Match.builder()
                    .id(id)
                    .source(ImportSource.FCTT)
                    .sourceFixtureId(acta.matchId())
                    .competition(context.competition())
                    .season(season)
                    .groupNumber(groupNumber)
                    .round(context.round())
                    .phase(context.phase())
                    .dateTime(toDateTime(acta))
                    .city(acta.venue() == null ? null : acta.venue().city())
                    .venue(acta.venue() == null ? null : acta.venue().venue())
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .winnerTeam(resolveWinnerTeam(acta, homeTeam, awayTeam, context))
                    .refereeName(refereeName(acta))
                    .homeGamesWon(gamesWon == null ? null : gamesWon.home())
                    .awayGamesWon(gamesWon == null ? null : gamesWon.away())
                    .homeSetsWon(setsWon == null ? null : setsWon.home())
                    .awaySetsWon(setsWon == null ? null : setsWon.away())
                    .protested(acta.wasProtested())
                    .status(MatchStatus.PLAYED);
            return existing ? builder.createExisting() : builder.createNew();
        }
    }

    /** Games, set scores and doubles pairs built from one report, not yet saved. */
    private record BuiltGames(List<Game> games, List<SetScore> setScores, List<DoublesPair> doublesPairs) {
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

    private Team resolveWinnerTeam(Acta acta, Team homeTeam, Team awayTeam,
                                         FcttMatchReportContext context) {
        if (acta.finalResult() == null) {
            return null;
        }
        String winnerName = acta.finalResult().winnerName();
        if (winnerName != null) {
            if (winnerName.equals(acta.teams().home().name())) {
                return homeTeam;
            }
            if (winnerName.equals(acta.teams().away().name())) {
                return awayTeam;
            }
            LOGGER.debug("FCTT winner \"{}\" matches neither team in {}; falling back to score",
                    winnerName, context.matchReportFile());
        }
        ActaScore gamesWon = acta.finalResult().gamesWon();
        if (gamesWon == null || gamesWon.home() == null || gamesWon.away() == null) {
            return null;
        }
        return gamesWon.home() > gamesWon.away() ? homeTeam
                : gamesWon.away() > gamesWon.home() ? awayTeam : null;
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
            playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.FCTT, player.license(), season)
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

    private List<Lineup> buildLineups(Match match, Team homeTeam, SideLineup home,
                                      Team awayTeam, SideLineup away) {
        List<Lineup> lineups = new ArrayList<>();
        addLineups(lineups, match, homeTeam, home);
        addLineups(lineups, match, awayTeam, away);
        return lineups;
    }

    private void addLineups(List<Lineup> lineups, Match match, Team team, SideLineup side) {
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
                    .team(team)
                    .letter(letter)
                    .position(position)
                    .player(player)
                    .ranking(ranking == null ? null : ranking.floatValue())
                    .createNew());
        });
    }

    private BuiltGames buildGames(FcttMatchReportContext context, Match match, SideLineup home, SideLineup away) {
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
        return new BuiltGames(games, setScores, doublesPairs);
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
                .createNew();
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
        return playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.FCTT, player.license(), season)
                .orElse(null);
    }

    private record SideLineup(Map<String, PlayerSeason> byLetter,
                              Map<String, PlayerSeason> byName,
                              Map<String, Double> rankingByLetter) {
    }
}
