package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FEAT-00089: the {@code source_checksum} column round-trips through the JPA adapter, a replacement
 * writes the new value, and {@code recordSourceChecksum} only touches a stored PLAYED match.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchSourceChecksumJpaTest {

    private static final Season SEASON = Season.of(2026);
    private static final String COMPETITION = "divisio-honor-checksum";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void playedMatchRoundTripsItsChecksum() {
        Team home = storedTeam("CS HOME", ImportSource.RFETM);
        Team away = storedTeam("CS AWAY", ImportSource.RFETM);
        Match played = playedCopy(UUID.randomUUID(), home, away, 1, "v1:abc");
        matchRepository.saveMatch(played);

        assertEquals("v1:abc", matchRepository.findMatchById(played.getId()).orElseThrow().getSourceChecksum());
    }

    @Test
    void scheduledMatchKeepsANullChecksum() {
        Team home = storedTeam("CS SCHED HOME", ImportSource.RFETM);
        Team away = storedTeam("CS SCHED AWAY", ImportSource.RFETM);
        Match scheduled = scheduledCopy(UUID.randomUUID(), home, away, 1);
        matchRepository.saveMatch(scheduled);

        assertNull(matchRepository.findMatchById(scheduled.getId()).orElseThrow().getSourceChecksum());
    }

    @Test
    void replaceMatchContentWritesTheNewChecksum() {
        Team home = storedTeam("CSR HOME", ImportSource.RFETM);
        Team away = storedTeam("CSR AWAY", ImportSource.RFETM);
        Match scheduled = scheduledCopy(UUID.randomUUID(), home, away, 1);
        matchRepository.saveMatch(scheduled);

        Match header = playedCopy(scheduled.getId(), home, away, 1, "v1:new");
        matchRepository.replaceMatchContent(
                new MatchContent(header, List.of(), List.of(), List.of(), List.of()));

        Match after = matchRepository.findMatchById(scheduled.getId()).orElseThrow();
        assertEquals(MatchStatus.PLAYED, after.getStatus());
        assertEquals("v1:new", after.getSourceChecksum());
    }

    @Test
    void recordSourceChecksumUpdatesOnlyAStoredPlayedMatch() {
        Team home = storedTeam("CSR2 HOME", ImportSource.RFETM);
        Team away = storedTeam("CSR2 AWAY", ImportSource.RFETM);
        Match played = playedCopy(UUID.randomUUID(), home, away, 1, null);
        matchRepository.saveMatch(played);

        matchRepository.recordSourceChecksum(played.getId(), "v1:adopted");
        assertEquals("v1:adopted", matchRepository.findMatchById(played.getId()).orElseThrow().getSourceChecksum());

        Team otherHome = storedTeam("CSR2 SCHED HOME", ImportSource.RFETM);
        Team otherAway = storedTeam("CSR2 SCHED AWAY", ImportSource.RFETM);
        Match scheduled = scheduledCopy(UUID.randomUUID(), otherHome, otherAway, 2);
        matchRepository.saveMatch(scheduled);

        assertThrows(IllegalStateException.class,
                () -> matchRepository.recordSourceChecksum(scheduled.getId(), "v1:x"));
        assertThrows(IllegalStateException.class,
                () -> matchRepository.recordSourceChecksum(UUID.randomUUID(), "v1:x"));
        assertThrows(NullPointerException.class,
                () -> matchRepository.recordSourceChecksum(played.getId(), null));
        assertThrows(NullPointerException.class,
                () -> matchRepository.recordSourceChecksum(null, "v1:x"));
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match scheduledCopy(UUID id, Team home, Team away, int round) {
        return Match.builder()
                .id(id)
                .source(home.getSource())
                .competition(COMPETITION)
                .season(SEASON)
                .groupNumber(1)
                .round(round)
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.SCHEDULED)
                .createExisting();
    }

    private Match playedCopy(UUID id, Team home, Team away, int round, String sourceChecksum) {
        return Match.builder()
                .id(id)
                .source(home.getSource())
                .competition(COMPETITION)
                .season(SEASON)
                .groupNumber(1)
                .round(round)
                .homeTeam(home)
                .awayTeam(away)
                .homeGamesWon(5)
                .awayGamesWon(2)
                .winnerTeam(home)
                .sourceChecksum(sourceChecksum)
                .status(MatchStatus.PLAYED)
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