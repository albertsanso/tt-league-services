package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.JornadaProgress;
import org.cttelsamicsterrassa.data.core.domain.match.model.JornadaProgressCalculator;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchCalendarEntry;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.OverdueGracePeriod;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00102: the slim calendar projection is source- and season-scoped, returns every status and
 * keeps null group, phase and date. The state rule itself is tested in the domain.
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchCalendarEntriesJpaTest {

    private static final Season SEASON = Season.of(2026);
    private static final String TERCERA = "tercera-nacional-masculino";
    private static final String PHASE = "1a Fase";

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;

    @Test
    void returnsEveryStatusOfTheSourceAndSeasonOnly() {
        Match played = storedMatch(ImportSource.FCTT, SEASON, 2, PHASE, 3, MatchStatus.PLAYED,
                LocalDate.of(2026, 9, 26));
        Match scheduled = storedMatch(ImportSource.FCTT, SEASON, 2, PHASE, 3, MatchStatus.SCHEDULED,
                LocalDate.of(2026, 9, 27));
        storedMatch(ImportSource.FCTT, Season.of(2025), 2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2025, 9, 27));
        storedMatch(ImportSource.RFETM, SEASON, 2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 27));

        List<MatchCalendarEntry> entries = matchRepository.findCalendarEntries(ImportSource.FCTT, SEASON);

        assertEquals(Set.of(played.getId(), scheduled.getId()),
                Set.copyOf(entries.stream().map(MatchCalendarEntry::matchId).toList()));
        MatchCalendarEntry entry = entries.stream()
                .filter(e -> e.matchId().equals(scheduled.getId())).findFirst().orElseThrow();
        assertEquals(TERCERA, entry.competition());
        assertEquals(2, entry.groupNumber());
        assertEquals(PHASE, entry.phase());
        assertEquals(3, entry.round());
        assertEquals(MatchStatus.SCHEDULED, entry.status());
        assertEquals(LocalDate.of(2026, 9, 27), entry.matchDate());
    }

    @Test
    void keepsNullGroupPhaseAndDate() {
        Match undated = storedMatch(ImportSource.FCTT, SEASON, null, null, 1, MatchStatus.SCHEDULED, null);

        MatchCalendarEntry entry = matchRepository.findCalendarEntries(ImportSource.FCTT, SEASON).stream()
                .filter(e -> e.matchId().equals(undated.getId())).findFirst().orElseThrow();

        assertNull(entry.groupNumber());
        assertNull(entry.phase());
        assertNull(entry.matchDate());
    }

    @Test
    void aSourceAndSeasonWithoutMatchesHasNoEntries() {
        assertTrue(matchRepository.findCalendarEntries(ImportSource.RFETM, SEASON).isEmpty());
    }

    @Test
    void bothArgumentsAreRequired() {
        assertThrows(NullPointerException.class, () -> matchRepository.findCalendarEntries(null, SEASON));
        assertThrows(NullPointerException.class,
                () -> matchRepository.findCalendarEntries(ImportSource.FCTT, null));
    }

    @Test
    void anOverdueScheduledMatchCountsAsOverdueThroughProgressAndEntries() {
        storedMatch(ImportSource.FCTT, SEASON, 2, PHASE, 3, MatchStatus.PLAYED, LocalDate.of(2026, 9, 26));
        storedMatch(ImportSource.FCTT, SEASON, 2, PHASE, 3, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 5));
        storedMatch(ImportSource.FCTT, SEASON, 2, PHASE, 1, MatchStatus.SCHEDULED, LocalDate.of(2026, 9, 12));

        List<JornadaProgress> jornadas = JornadaProgressCalculator.compute(
                matchRepository.findRoundProgress(ImportSource.FCTT, SEASON),
                matchRepository.findCalendarEntries(ImportSource.FCTT, SEASON),
                Set.of(), LocalDate.of(2026, 9, 29), OverdueGracePeriod.DEFAULT);

        assertEquals(2, jornadas.size());
        assertEquals(1, jornadas.get(0).round());
        assertEquals(1, jornadas.get(0).postponedMatches(), "round 1 is below the current round 3");
        assertEquals(3, jornadas.get(1).round());
        assertEquals(1, jornadas.get(1).overdueMatches());
        assertEquals(1, jornadas.get(1).playedMatches());
        assertEquals(LocalDate.of(2026, 9, 5), jornadas.get(1).firstDate());
        assertEquals(LocalDate.of(2026, 9, 26), jornadas.get(1).lastDate());
    }

    private Match storedMatch(ImportSource source, Season season, Integer groupNumber, String phase, int round,
                              MatchStatus status, LocalDate date) {
        Team home = storedTeam(source, season, "HOME " + UUID.randomUUID());
        Team away = storedTeam(source, season, "AWAY " + UUID.randomUUID());
        Match.MatchBuilder builder = Match.builder()
                .id(UUID.randomUUID())
                .source(source)
                .competition(TERCERA)
                .season(season)
                .groupNumber(groupNumber)
                .round(round)
                .phase(phase)
                .homeTeam(home)
                .awayTeam(away);
        if (date != null) {
            builder.dateTime(ZonedDateTime.of(date, LocalTime.of(18, 0), Match.COMPETITION_ZONE));
        }
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
