package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00086: the {@code findMatchesBySourceSeasonAndStatus} port on the JPA adapter. The query is
 * source- and season-scoped, filters by status and never writes.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchByStatusJpaTest {

    private static final Season SEASON = Season.of(2026);
    private static final Season OTHER_SEASON = Season.of(2025);
    private static final String COMPETITION = "tercera-nacional-sfd";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void returnsOnlyTheRequestedSourceSeasonAndStatus() {
        Match rfetmScheduled = storedScheduled(ImportSource.RFETM, SEASON, 1, MatchStatus.SCHEDULED);
        Match rfetmPlayed = storedScheduled(ImportSource.RFETM, SEASON, 2, MatchStatus.PLAYED);
        storedScheduled(ImportSource.RFETM, OTHER_SEASON, 1, MatchStatus.SCHEDULED);
        storedScheduled(ImportSource.BCNESA, SEASON, 1, MatchStatus.SCHEDULED);

        List<Match> found = matchRepository.findMatchesBySourceSeasonAndStatus(
                ImportSource.RFETM, SEASON, MatchStatus.SCHEDULED);

        assertEquals(List.of(rfetmScheduled.getId()), found.stream().map(Match::getId).toList());
        assertTrue(found.stream().noneMatch(match -> match.getId().equals(rfetmPlayed.getId())));
    }

    @Test
    void emptyListWhenNothingMatches() {
        storedScheduled(ImportSource.RFETM, SEASON, 1, MatchStatus.PLAYED);

        assertTrue(matchRepository.findMatchesBySourceSeasonAndStatus(
                ImportSource.RFETM, SEASON, MatchStatus.SCHEDULED).isEmpty());
        assertTrue(matchRepository.findMatchesBySourceSeasonAndStatus(
                ImportSource.FCTT, SEASON, MatchStatus.PLAYED).isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndStatus(null, SEASON, MatchStatus.SCHEDULED));
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndStatus(ImportSource.RFETM, null, MatchStatus.SCHEDULED));
        assertThrows(NullPointerException.class, () -> matchRepository
                .findMatchesBySourceSeasonAndStatus(ImportSource.RFETM, SEASON, null));
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match storedScheduled(ImportSource source, Season season, int round, MatchStatus status) {
        Team home = storedTeam(source, season, "HOME " + UUID.randomUUID());
        Team away = storedTeam(source, season, "AWAY " + UUID.randomUUID());
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(COMPETITION)
                .season(season)
                .groupNumber(1)
                .round(round)
                .homeTeam(home)
                .awayTeam(away);
        if (status == MatchStatus.PLAYED) {
            builder.homeGamesWon(5).awayGamesWon(2).winnerTeam(home);
        }
        Match match = builder.status(status).createExisting();
        matchRepository.saveMatch(match);
        return match;
    }

    private Team storedTeam(ImportSource source, Season season, String name) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, season, club);
        teamRepository.saveTeam(team);
        return team;
    }
}
