package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchSchedule;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.MatchRepositoryHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00083: the source fixture id ({@code id_partido}) column, its source-scoped unique
 * constraint and the {@code findBySourceFixtureId} port on the JPA adapter.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchSourceFixtureIdJpaTest {

    private static final Season SEASON = Season.of(2026);
    private static final String COMPETITION = "thirdera-nacional-sfd";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchRepositoryHelper matchRepositoryHelper;

    @Test
    void savedFixtureIdReloadsAndIsFoundSourceScoped() {
        Match match = storedScheduled("FIX HOME", "FIX AWAY", ImportSource.RFETM, 1, "ES_03_26_0001234_0001");

        Match reloaded = matchRepository.findMatchById(match.getId()).orElseThrow();
        assertEquals("ES_03_26_0001234_0001", reloaded.getSourceFixtureId());
        assertEquals(match.getId(),
                matchRepository.findBySourceFixtureId(ImportSource.RFETM, "ES_03_26_0001234_0001")
                        .orElseThrow().getId());
        assertTrue(matchRepository.findBySourceFixtureId(ImportSource.BCNESA, "ES_03_26_0001234_0001").isEmpty());
    }

    @Test
    void legacyRowsWithoutFixtureIdStayNullAndDoNotCollide() {
        Match first = storedScheduled("NULL HOME", "NULL AWAY", ImportSource.RFETM, 1, null);
        storedScheduled("NULL HOME 2", "NULL AWAY 2", ImportSource.RFETM, 2, null);
        matchRepositoryHelper.flush();

        assertNull(matchRepository.findMatchById(first.getId()).orElseThrow().getSourceFixtureId());
    }

    @Test
    void duplicateFixtureIdWithinOneSourceViolatesTheUniqueConstraint() {
        Team home = storedTeam("DUP HOME", ImportSource.RFETM);
        Team away = storedTeam("DUP AWAY", ImportSource.RFETM);
        storedScheduled(home, away, 1, "ES_03_26_0009999_0001");
        Match duplicate = scheduledCopy(home, away, 2, "ES_03_26_0009999_0001");

        assertThrows(DataIntegrityViolationException.class, () -> {
            matchRepository.saveMatch(duplicate);
            matchRepositoryHelper.flush();
        });
    }

    @Test
    void sameFixtureIdUnderTwoSourcesIsAllowed() {
        storedScheduled("CROSS HOME", "CROSS AWAY", ImportSource.RFETM, 1, "SHARED_1");
        storedScheduled("CROSS HOME 2", "CROSS AWAY 2", ImportSource.BCNESA, 1, "SHARED_1");
        matchRepositoryHelper.flush();

        assertEquals(1, matchRepository.findBySourceFixtureId(ImportSource.RFETM, "SHARED_1").stream().count());
        assertTrue(matchRepository.findBySourceFixtureId(ImportSource.BCNESA, "SHARED_1").isPresent());
    }

    @Test
    void maxLengthFixtureIdPersists() {
        String value = "x".repeat(Match.SOURCE_FIXTURE_ID_MAX_LENGTH);
        Match match = storedScheduled("LONG HOME", "LONG AWAY", ImportSource.RFETM, 1, value);

        assertEquals(value, matchRepository.findMatchById(match.getId()).orElseThrow().getSourceFixtureId());
    }

    @Test
    void replaceMatchContentWritesTheNewHeaderFixtureId() {
        Team home = storedTeam("REPL HOME", ImportSource.RFETM);
        Team away = storedTeam("REPL AWAY", ImportSource.RFETM);
        Match scheduled = scheduledCopy(home, away, 1, null);
        matchRepository.saveMatch(scheduled);

        Match playedHeader = Match.builder().id(scheduled.getId()).source(ImportSource.RFETM)
                .sourceFixtureId("ES_03_26_0007777_0001")
                .competition(COMPETITION).season(SEASON).groupNumber(1).round(1)
                .homeTeam(home).awayTeam(away)
                .homeGamesWon(5).awayGamesWon(2).winnerTeam(home)
                .status(MatchStatus.PLAYED).createExisting();
        matchRepository.replaceMatchContent(
                new MatchContent(playedHeader, List.of(), List.of(), List.of(), List.of()));

        Match after = matchRepository.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals("ES_03_26_0007777_0001", after.getSourceFixtureId());
        assertEquals(MatchStatus.PLAYED, after.getStatus());
    }

    @Test
    void updateScheduleLeavesTheFixtureIdUntouched() {
        Match scheduled = storedScheduled("SCHED HOME", "SCHED AWAY", ImportSource.RFETM, 1, "ES_03_26_0005555_0001");

        ZonedDateTime newDateTime = ZonedDateTime.of(2026, 10, 3, 19, 0, 0, 0, Match.COMPETITION_ZONE);
        matchRepository.updateSchedule(scheduled.getId(),
                new MatchSchedule(newDateTime, "Sabadell", "Can Deu Batteria", "Jane Doe", "LIC-9"));

        Match after = matchRepository.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals("ES_03_26_0005555_0001", after.getSourceFixtureId());
        assertEquals(newDateTime, after.getDateTime());
        assertEquals("Sabadell", after.getCity());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match storedScheduled(String homeName, String awayName, ImportSource source, int round,
                                  String sourceFixtureId) {
        return storedScheduled(storedTeam(homeName, source), storedTeam(awayName, source), round, sourceFixtureId);
    }

    private Match storedScheduled(Team home, Team away, int round, String sourceFixtureId) {
        Match match = scheduledCopy(home, away, round, sourceFixtureId);
        matchRepository.saveMatch(match);
        return match;
    }

    private Match scheduledCopy(Team home, Team away, int round, String sourceFixtureId) {
        return Match.builder()
                .id(UUID.randomUUID())
                .source(home.getSource())
                .sourceFixtureId(sourceFixtureId)
                .competition(COMPETITION)
                .season(SEASON)
                .groupNumber(1)
                .round(round)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.SCHEDULED)
                .createExisting();
    }

    private Team storedTeam(String name, ImportSource source) {
        FederatedClub club = FederatedClub.createNew(source, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), source, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }
}
