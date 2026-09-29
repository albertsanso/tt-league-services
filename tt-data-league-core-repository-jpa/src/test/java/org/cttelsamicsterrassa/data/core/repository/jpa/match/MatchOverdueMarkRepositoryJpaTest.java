package org.cttelsamicsterrassa.data.core.repository.jpa.match;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.match.model.Match;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchContent;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchStatus;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.MatchOverdueMarkRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.MatchRepositoryHelper;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchOverdueMarkJPA;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEAT-00092: the manual overdue mark persistence. One row per match, an FK to the match, and the
 * mark left untouched by a {@code replaceMatchContent} upgrade (import writes never touch operator
 * data).
 */
@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class MatchOverdueMarkRepositoryJpaTest {

    private static final ImportSource SOURCE = ImportSource.FCTT;
    private static final Season SEASON = Season.of(2026);
    private static final String COMPETITION = "tercera-nacional-sfd";
    private static final ZonedDateTime MARKED_AT = ZonedDateTime.of(
            2026, 9, 8, 10, 0, 0, 0, Match.COMPETITION_ZONE);

    @Autowired
    private FederatedClubRepository clubRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchOverdueMarkRepository markRepository;
    @Autowired
    private MatchOverdueMarkRepositoryHelper markHelper;
    @Autowired
    private MatchRepositoryHelper matchHelper;

    @Test
    void saveFindFindByIdsAndDeleteRoundTrip() {
        Match match = storedScheduled();
        markRepository.save(new MatchOverdueMark(match.getId(), MARKED_AT, "admin"));

        MatchOverdueMark found = markRepository.findByMatchId(match.getId()).orElseThrow();
        assertEquals(match.getId(), found.matchId());
        assertEquals(MARKED_AT.toInstant(), found.markedAt().toInstant());
        assertEquals("admin", found.markedBy());

        Match second = storedScheduled();
        markRepository.save(new MatchOverdueMark(second.getId(), MARKED_AT.plusDays(1), "supervisor"));
        List<MatchOverdueMark> marks = markRepository.findByMatchIds(List.of(match.getId(), second.getId()));
        assertEquals(2, marks.size());

        assertTrue(markRepository.deleteByMatchId(match.getId()));
        assertTrue(markRepository.findByMatchId(match.getId()).isEmpty());
        assertFalse(markRepository.deleteByMatchId(match.getId()), "deleting a missing row reports false");
    }

    @Test
    void savingTwiceKeepsTheFirstMarkedAtAndAuthor() {
        Match match = storedScheduled();
        markRepository.save(new MatchOverdueMark(match.getId(), MARKED_AT, "first"));
        markRepository.save(new MatchOverdueMark(match.getId(), MARKED_AT.plusDays(5), "later"));

        assertEquals(MARKED_AT.toInstant(),
                markRepository.findByMatchId(match.getId()).orElseThrow().markedAt().toInstant());
        assertEquals("first", markRepository.findByMatchId(match.getId()).orElseThrow().markedBy());
    }

    @Test
    void anEmptyIdListIssuesNoQuery() {
        assertTrue(markRepository.findByMatchIds(List.of()).isEmpty());
    }

    @Test
    void anUnknownMatchIdIsRejectedByTheForeignKey() {
        MatchJPA proxy = matchHelper.getReferenceById(UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class, () -> markHelper
                .saveAndFlush(new MatchOverdueMarkJPA(proxy, MARKED_AT, "admin")));
    }

    @Test
    void aMarkSurvivesAReplaceMatchContentUpgrade() {
        Match scheduled = storedScheduled();
        markRepository.save(new MatchOverdueMark(scheduled.getId(), MARKED_AT, "admin"));

        Match upgraded = Match.builder()
                .id(scheduled.getId())
                .source(scheduled.getSource())
                .competition(scheduled.getCompetition())
                .season(scheduled.getSeason())
                .groupNumber(scheduled.getGroupNumber())
                .round(scheduled.getRound())
                .phase(scheduled.getPhase())
                .dateTime(scheduled.getDateTime())
                .homeTeam(scheduled.getHomeTeam())
                .awayTeam(scheduled.getAwayTeam())
                .winnerTeam(scheduled.getHomeTeam())
                .homeGamesWon(5)
                .awayGamesWon(2)
                .status(MatchStatus.PLAYED)
                .createExisting();
        matchRepository.replaceMatchContent(new MatchContent(upgraded, List.of(), List.of(), List.of(), List.of()));

        assertEquals(MARKED_AT.toInstant(),
                markRepository.findByMatchId(scheduled.getId()).orElseThrow().markedAt().toInstant());
    }

    // --- fixtures ----------------------------------------------------------------------------

    private Match storedScheduled() {
        Team home = storedTeam("HOME " + UUID.randomUUID());
        Team away = storedTeam("AWAY " + UUID.randomUUID());
        Match match = Match.builder()
                .id(UUID.randomUUID())
                .source(SOURCE)
                .competition(COMPETITION)
                .season(SEASON)
                .groupNumber(1)
                .round(1)
                .phase("1a Fase")
                .homeTeam(home)
                .awayTeam(away)
                .status(MatchStatus.SCHEDULED)
                .createExisting();
        matchRepository.saveMatch(match);
        return match;
    }

    private Team storedTeam(String name) {
        FederatedClub club = FederatedClub.createNew(SOURCE, name);
        clubRepository.saveFederatedClub(club);
        Team team = Team.createExisting(UUID.randomUUID(), SOURCE, name, SEASON, club);
        teamRepository.saveTeam(team);
        return team;
    }
}