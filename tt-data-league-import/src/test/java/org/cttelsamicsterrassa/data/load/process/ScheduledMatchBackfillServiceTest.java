package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.ScheduledMatchBackfillCandidate;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.rfetm.process.MatchContextProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmMatchImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPlayerImportProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmTeamImportProcessor;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillMode;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillService;
import org.cttelsamicsterrassa.data.load.shared.match.backfill.ScheduledMatchBackfillSummary;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00078: exercises the backfill service against matches produced by the real RFETM processors
 * (for the legacy-empty and "decided 0-0" fixtures) plus a few hand-built matches for the shapes no
 * fixture covers (a real winner, a played draw with game-level results, a walkover, and out-of-scope
 * rows), through the in-memory repositories.
 */
class ScheduledMatchBackfillServiceTest {

    private static final ImportSource SOURCE = ImportSource.RFETM;
    private static final Season SEASON = Season.fromFormatted("2025-2026");

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;
    private InMemoryRepositories.Matches matches;
    private InMemoryRepositories.Lineups lineups;
    private InMemoryRepositories.Games games;
    private InMemoryRepositories.SetScores setScores;
    private InMemoryRepositories.DoublesPairs doublesPairs;
    private ScheduledMatchBackfillService service;

    private UUID legacyEmptyMatchId;
    private UUID decidedZeroZeroMatchId;
    private UUID playedWithWinnerMatchId;
    private UUID playedDrawMatchId;
    private UUID walkoverMatchId;
    private UUID otherSeasonCandidateId;
    private UUID otherSourceCandidateId;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
        matches = new InMemoryRepositories.Matches();
        lineups = new InMemoryRepositories.Lineups(playerSeasons);
        games = new InMemoryRepositories.Games();
        setScores = new InMemoryRepositories.SetScores();
        doublesPairs = new InMemoryRepositories.DoublesPairs();

        List<MatchContextProcessor> processors = List.of(
                new RfetmTeamImportProcessor(teams),
                new RfetmPlayerImportProcessor(playerSeasons),
                new RfetmMatchImportProcessor(teams, playerSeasons, matches, lineups, games, setScores, doublesPairs));

        InMemoryRepositories.ScheduledMatchBackfill repository =
                new InMemoryRepositories.ScheduledMatchBackfill(matches, games, lineups, setScores, doublesPairs);
        service = new ScheduledMatchBackfillService(repository);

        seed(processors);
    }

    private void seed(List<MatchContextProcessor> processors) {
        // FEAT-00081 imports the legacy-empty and decided 0-0 shapes directly as SCHEDULED, so the
        // stored PLAYED placeholder rows this backfill repairs are restored here to the pre-feature
        // state the FEAT-00077 default produced: same header, status PLAYED.
        int before = matches.saved.size();
        run(processors, legacyEmptyContext());
        legacyEmptyMatchId = lastAdded(before);
        restoreLegacyPlayedPlaceholder(legacyEmptyMatchId);

        before = matches.saved.size();
        run(processors, decidedZeroZeroContext());
        decidedZeroZeroMatchId = lastAdded(before);
        restoreLegacyPlayedPlaceholder(decidedZeroZeroMatchId);

        before = matches.saved.size();
        run(processors, playedWithWinnerContext());
        playedWithWinnerMatchId = lastAdded(before);

        before = matches.saved.size();
        run(processors, playedDrawContext());
        playedDrawMatchId = lastAdded(before);

        before = matches.saved.size();
        run(processors, walkoverContext());
        walkoverMatchId = lastAdded(before);

        otherSeasonCandidateId = seedOutOfScopeCandidate(Season.fromFormatted("2024-2025"), ImportSource.RFETM);
        otherSourceCandidateId = seedOutOfScopeCandidate(SEASON, ImportSource.BCNESA);
    }

    @Test
    void reportListsExactlyTheLegacyEmptyAndDecidedZeroZeroMatchesAndWritesNothing() {
        ScheduledMatchBackfillSummary summary = service.run(SOURCE, SEASON, ScheduledMatchBackfillMode.REPORT);

        assertEquals(ScheduledMatchBackfillMode.REPORT, summary.mode());
        Set<UUID> candidateIds = candidateIds(summary.candidates());
        assertEquals(Set.of(legacyEmptyMatchId, decidedZeroZeroMatchId), candidateIds);
        assertEquals(0, summary.written().matchesUpdated());

        assertEquals(MatchStatus.PLAYED, matchById(legacyEmptyMatchId).getStatus());
        assertEquals(MatchStatus.PLAYED, matchById(decidedZeroZeroMatchId).getStatus());
        assertFalse(games.saved.isEmpty());
        assertFalse(candidateIds.contains(otherSeasonCandidateId));
        assertFalse(candidateIds.contains(otherSourceCandidateId));
    }

    @Test
    void writeMarksOnlyTheLegacyEmptyAndDecidedZeroZeroMatchesScheduledAndDeletesTheirChildren() {
        service.run(SOURCE, SEASON, ScheduledMatchBackfillMode.WRITE);

        assertScheduledWithNoChildren(legacyEmptyMatchId);
        assertScheduledWithNoChildren(decidedZeroZeroMatchId);

        assertStillPlayedAndUntouched(playedWithWinnerMatchId);
        assertStillPlayedAndUntouched(playedDrawMatchId);
        assertStillPlayedAndUntouched(walkoverMatchId);
        assertStillPlayedAndUntouched(otherSeasonCandidateId);
        assertStillPlayedAndUntouched(otherSourceCandidateId);
    }

    @Test
    void aSecondWriteFindsNoCandidates() {
        service.run(SOURCE, SEASON, ScheduledMatchBackfillMode.WRITE);

        ScheduledMatchBackfillSummary second = service.run(SOURCE, SEASON, ScheduledMatchBackfillMode.WRITE);

        assertTrue(second.candidates().isEmpty());
        assertEquals(0, second.written().matchesUpdated());
    }

    @Test
    void markScheduledWithANonCandidateIdFailsAndWritesNothing() {
        InMemoryRepositories.ScheduledMatchBackfill repository =
                new InMemoryRepositories.ScheduledMatchBackfill(matches, games, lineups, setScores, doublesPairs);

        assertThrows(IllegalStateException.class,
                () -> repository.markScheduled(SOURCE, SEASON, List.of(legacyEmptyMatchId, playedWithWinnerMatchId)));

        assertEquals(MatchStatus.PLAYED, matchById(legacyEmptyMatchId).getStatus());
        assertEquals(MatchStatus.PLAYED, matchById(playedWithWinnerMatchId).getStatus());
    }

    @Test
    void nullSourceOrSeasonFails() {
        InMemoryRepositories.ScheduledMatchBackfill repository =
                new InMemoryRepositories.ScheduledMatchBackfill(matches, games, lineups, setScores, doublesPairs);

        assertThrows(NullPointerException.class, () -> repository.findScheduledBackfillCandidates(null, SEASON));
        assertThrows(NullPointerException.class, () -> repository.findScheduledBackfillCandidates(SOURCE, null));
        assertThrows(NullPointerException.class,
                () -> repository.markScheduled(null, SEASON, List.of(legacyEmptyMatchId)));
        assertThrows(NullPointerException.class,
                () -> repository.markScheduled(SOURCE, null, List.of(legacyEmptyMatchId)));
    }

    private void assertScheduledWithNoChildren(UUID matchId) {
        Match match = matchById(matchId);
        assertEquals(MatchStatus.SCHEDULED, match.getStatus());
        assertNull(match.getWinnerTeam());
        assertNull(match.getHomeGamesWon());
        assertNull(match.getAwayGamesWon());
        assertNull(match.getHomeSetsWon());
        assertNull(match.getAwaySetsWon());
        assertTrue(games.saved.stream().noneMatch(game -> matchId.equals(game.getMatch().getId())));
        assertTrue(lineups.saved.stream().noneMatch(lineup -> matchId.equals(lineup.getMatch().getId())));
    }

    private void assertStillPlayedAndUntouched(UUID matchId) {
        assertEquals(MatchStatus.PLAYED, matchById(matchId).getStatus());
    }

    private Match matchById(UUID matchId) {
        return matches.saved.stream()
                .filter(match -> matchId.equals(match.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Match not found: " + matchId));
    }

    /**
     * Rewrites the stored fixture as the pre-FEAT-00081 row the backfill repairs: same id and header,
     * status PLAYED. FEAT-00081 imports these shapes directly as SCHEDULED, so the legacy stored state
     * must be restored for the backfill service to have candidates.
     */
    private void restoreLegacyPlayedPlaceholder(UUID matchId) {
        Match stored = matchById(matchId);
        matches.saved.set(matches.saved.indexOf(stored), Match.builder()
                .id(stored.getId())
                .source(stored.getSource())
                .externalId(stored.getExternalId())
                .competition(stored.getCompetition())
                .season(stored.getSeason())
                .groupNumber(stored.getGroupNumber())
                .round(stored.getRound())
                .phase(stored.getPhase())
                .dateTime(stored.getDateTime())
                .city(stored.getCity())
                .venue(stored.getVenue())
                .homeTeam(stored.getHomeTeam())
                .awayTeam(stored.getAwayTeam())
                .winnerTeam(stored.getWinnerTeam())
                .refereeName(stored.getRefereeName())
                .refereeLicense(stored.getRefereeLicense())
                .homeGamesWon(stored.getHomeGamesWon())
                .awayGamesWon(stored.getAwayGamesWon())
                .homeSetsWon(stored.getHomeSetsWon())
                .awaySetsWon(stored.getAwaySetsWon())
                .protested(stored.isProtested())
                .status(MatchStatus.PLAYED)
                .createExisting());
    }

    private static Set<UUID> candidateIds(List<ScheduledMatchBackfillCandidate> candidates) {
        return candidates.stream().map(ScheduledMatchBackfillCandidate::matchId).collect(Collectors.toSet());
    }

    private UUID lastAdded(int sizeBefore) {
        assertEquals(sizeBefore + 1, matches.saved.size(), "expected exactly one new match to be stored");
        return matches.saved.get(sizeBefore).getId();
    }

    private void run(List<MatchContextProcessor> processors, MatchReportContext context) {
        processors.forEach(processor -> processor.process(context));
    }

    /** (a) A legacy empty acta: null scores, no winner, no games, no lineups. */
    private static MatchReportContext legacyEmptyContext() {
        Path file = fixture("acta_rfetm_2026_unpublished.json");
        Acta acta = new ActaParser().parse(file);
        return new MatchReportContext("2025-2026", "divisio-honor", "1", "femenino",
                RfetmClubKey.ofFederationId("20201878", "VR ARENA LINARES"),
                RfetmClubKey.ofFederationId("1052", "TECNIGEN LINARES"), file, acta);
    }

    /** (b) A G17 "decided 0-0": header 0-0, no winner, home lineup present, every game not played. */
    private static MatchReportContext decidedZeroZeroContext() {
        Path file = fixture("acta_rfetm_2025_decided_0_0.json");
        Acta acta = new ActaParser().parse(file);
        String competition = MatchReportContext.competitionOf("divisio-honor", "femenino");
        return new MatchReportContext("2025-2026", "divisio-honor", "5", "femenino",
                RfetmClubKey.ofName("2025-2026", competition, "T.M.DEFENSE LA PALMA"),
                RfetmClubKey.ofName("2025-2026", competition, "ETM TORRELAVEGA"), file, acta);
    }

    /** (c) A real played match with a winner and games with set scores. */
    private static MatchReportContext playedWithWinnerContext() {
        Path file = fixture("acta_doubles.json");
        Acta acta = new ActaParser().parse(file);
        return new MatchReportContext("2025-2026", "divisio-honor", "1", "femenino",
                RfetmClubKey.ofFederationId("16207", null), RfetmClubKey.ofFederationId("2017543", null), file, acta);
    }

    /** (d) A played draw: no header winner, but individual games each have a winner. */
    private static MatchReportContext playedDrawContext() {
        Path file = fixture("acta_singles.json");
        Acta acta = new ActaParser().parse(file);
        return new MatchReportContext("2025-2026", "super-divisio", "1", "masculino",
                RfetmClubKey.ofFederationId("193", null), RfetmClubKey.ofFederationId("23", null), file, acta);
    }

    /** (e) A walkover-style not_played game that still names a winner; must count as a result. */
    private static MatchReportContext walkoverContext() {
        Path file = fixture("acta_rfetm_2025_decided_0_0.json");
        Acta original = new ActaParser().parse(file);
        Acta withWalkover = withRound(withWalkoverWinner(original), 6);
        String competition = MatchReportContext.competitionOf("divisio-honor", "femenino");
        return new MatchReportContext("2025-2026", "divisio-honor", "6", "femenino",
                RfetmClubKey.ofName("2025-2026", competition, "T.M.DEFENSE LA PALMA"),
                RfetmClubKey.ofName("2025-2026", competition, "ETM TORRELAVEGA"), file, withWalkover);
    }

    private static Acta withWalkoverWinner(Acta original) {
        List<ActaGame> gamesWithWalkover = original.games().stream()
                .map(game -> game.number() != null && game.number() == 1
                        ? new ActaGame(game.number(), game.type(), game.crossover(), game.home(), game.away(),
                                game.sets(), game.setsWon(), ActaGame.WINNER_HOME, game.cumulativeScore(),
                                true, "W.O.")
                        : game)
                .toList();
        return new Acta(original.matchId(), original.published(), original.federation(), original.season(),
                original.competition(), original.group(), original.round(), original.phase(), original.gender(),
                original.date(), original.time(), original.venue(), original.teams(), original.abcIsHome(),
                original.officials(), original.lineups(), original.doubles(), gamesWithWalkover,
                original.finalResult(), original.protested());
    }

    private static Acta withRound(Acta original, Integer round) {
        return new Acta(original.matchId(), original.published(), original.federation(), original.season(),
                original.competition(), original.group(), round, original.phase(), original.gender(),
                original.date(), original.time(), original.venue(), original.teams(), original.abcIsHome(),
                original.officials(), original.lineups(), original.doubles(), original.games(),
                original.finalResult(), original.protested());
    }

    /**
     * A candidate-shaped match (null everything, PLAYED, no children) outside the (source, season)
     * scope under test, to prove the rule never selects across scopes.
     */
    private UUID seedOutOfScopeCandidate(Season season, ImportSource source) {
        Team homeTeam = Team.createExisting(UUID.randomUUID(), source, "Out of scope home", season, null);
        Team awayTeam = Team.createExisting(UUID.randomUUID(), source, "Out of scope away", season, null);
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition("divisio-honor-femenino")
                .season(season)
                .groupNumber(1)
                .round(1)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .status(MatchStatus.PLAYED)
                .createExisting();
        matches.saved.add(match);
        return match.getId();
    }

    private static Path fixture(String name) {
        URL resource = ScheduledMatchBackfillServiceTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
