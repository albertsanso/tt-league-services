package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

public interface MatchRepositoryHelper extends JpaRepository<MatchJPA, UUID> {

    Optional<MatchJPA> findByExternalId(String externalId);

    /**
     * FEAT-00083: exact source-scoped lookup by the source fixture id ({@code id_partido}). The
     * {@code uk_match_source_fixture_id} unique constraint makes the result at most one row.
     */
    Optional<MatchJPA> findBySourceAndSourceFixtureId(Source source, String sourceFixtureId);

    /**
     * FEAT-00086: every match of one source, season and status, for snapshot reconciliation.
     * Served by {@code idx_match_source_season_competition_status}.
     */
    List<MatchJPA> findAllBySourceAndSeasonAndStatus(Source source, String season, MatchStatus status);

    /**
     * FEAT-00092 calendar read: every match of one source, season and competition, regardless of
     * status. This is the season-calendar read and must not feed statistics or any PLAYED-only view.
     * Ordered by group, phase, round, date and time (nulls last on group, phase and schedule
     * columns), served by {@code idx_match_source_season_competition_status}. Every fetch join is
     * to-one, so rows are never duplicated; {@code distinct} must not be added because PostgreSQL
     * rejects {@code SELECT DISTINCT} with ORDER BY expressions outside the select list.
     */
    @Query("""
            select m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            where m.source = :source and m.season = :season and m.competition = :competition
            order by case when m.groupNumber is null then 1 else 0 end asc,
                     m.groupNumber asc,
                     case when m.phase is null then 1 else 0 end asc,
                     m.phase asc,
                     m.round asc,
                     case when m.matchDate is null then 1 else 0 end asc,
                     m.matchDate asc,
                     case when m.matchTime is null then 1 else 0 end asc,
                     m.matchTime asc,
                     m.id asc
            """)
    List<MatchJPA> findAllBySourceAndSeasonAndCompetition(@Param("source") Source source,
                                                          @Param("season") String season,
                                                          @Param("competition") String competition);

    /** FEAT-00093: every match of a source and season whose date is in {@code [from, to)}. */
    @Query("""
            select m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            where m.source = :source and m.season = :season
              and m.matchDate >= :from and m.matchDate < :to
            order by m.matchDate asc,
                     case when m.matchTime is null then 1 else 0 end asc,
                     m.matchTime asc,
                     m.competition asc,
                     case when m.groupNumber is null then 1 else 0 end asc,
                     m.groupNumber asc,
                     m.round asc,
                     m.id asc
            """)
    List<MatchJPA> findAllBySourceAndSeasonAndMatchDateRange(@Param("source") Source source,
                                                             @Param("season") String season,
                                                             @Param("from") java.time.LocalDate from,
                                                             @Param("to") java.time.LocalDate to);

    @Query("""
            select m from MatchJPA m
            where m.source = :source and m.season = :season
              and m.status = :status
              and (:competition is null or m.competition = :competition)
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:clubNameF0 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%')))
              and (:clubNameF1 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF1, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF1, '%')))
              and (:clubNameF2 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF2, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF2, '%')))
              and (:clubNameF3 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF3, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF3, '%')))
              and (:clubNameF4 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF4, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF4, '%')))
              and (:playerNameF0 = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and lower(p.name) like lower(concat('%', :playerNameF0, '%'))
                    and (:playerNameF1 = '' or lower(p.name) like lower(concat('%', :playerNameF1, '%')))
                    and (:playerNameF2 = '' or lower(p.name) like lower(concat('%', :playerNameF2, '%')))
                    and (:playerNameF3 = '' or lower(p.name) like lower(concat('%', :playerNameF3, '%')))
                    and (:playerNameF4 = '' or lower(p.name) like lower(concat('%', :playerNameF4, '%')))
                    and (:playerLocation = 'EITHER'
                         or (:playerLocation = 'HOME' and l.team = m.homeTeam)
                         or (:playerLocation = 'AWAY' and l.team = m.awayTeam))))
              and (:playerId is null or exists (
                  select l2.id from LineupJPA l2
                  where l2.match = m and l2.player.id = :playerId and l2.source = :source
                    and (:playerLocation = 'EITHER'
                         or (:playerLocation = 'HOME' and l2.team = m.homeTeam)
                         or (:playerLocation = 'AWAY' and l2.team = m.awayTeam))))
            order by case when m.matchDate is null then 1 else 0 end asc,
                     m.matchDate desc,
                     case when m.matchTime is null then 1 else 0 end asc,
                     m.matchTime desc,
                     m.id asc
            """)
    List<MatchJPA> search(@Param("source") Source source, @Param("season") String season,
                          @Param("competition") String competition,
                          @Param("fromDate") java.time.LocalDate fromDate,
                          @Param("toDate") java.time.LocalDate toDate,
                          @Param("playerId") UUID playerId,
                          @Param("playerLocation") String playerLocation,
                          @Param("playerNameF0") String playerNameF0, @Param("playerNameF1") String playerNameF1,
                          @Param("playerNameF2") String playerNameF2, @Param("playerNameF3") String playerNameF3,
                          @Param("playerNameF4") String playerNameF4,
                          @Param("clubNameF0") String clubNameF0, @Param("clubNameF1") String clubNameF1,
                          @Param("clubNameF2") String clubNameF2, @Param("clubNameF3") String clubNameF3,
                          @Param("clubNameF4") String clubNameF4, @Param("status") MatchStatus status,
                          Pageable pageable);

    @Query("""
            select count(m) from MatchJPA m
            where m.source = :source and m.season = :season
              and m.status = :status
              and (:competition is null or m.competition = :competition)
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:clubNameF0 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%')))
              and (:clubNameF1 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF1, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF1, '%')))
              and (:clubNameF2 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF2, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF2, '%')))
              and (:clubNameF3 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF3, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF3, '%')))
              and (:clubNameF4 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF4, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF4, '%')))
              and (:playerNameF0 = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and lower(p.name) like lower(concat('%', :playerNameF0, '%'))
                    and (:playerNameF1 = '' or lower(p.name) like lower(concat('%', :playerNameF1, '%')))
                    and (:playerNameF2 = '' or lower(p.name) like lower(concat('%', :playerNameF2, '%')))
                    and (:playerNameF3 = '' or lower(p.name) like lower(concat('%', :playerNameF3, '%')))
                    and (:playerNameF4 = '' or lower(p.name) like lower(concat('%', :playerNameF4, '%')))
                    and (:playerLocation = 'EITHER'
                         or (:playerLocation = 'HOME' and l.team = m.homeTeam)
                         or (:playerLocation = 'AWAY' and l.team = m.awayTeam))))
              and (:playerId is null or exists (
                  select l2.id from LineupJPA l2
                  where l2.match = m and l2.player.id = :playerId and l2.source = :source
                    and (:playerLocation = 'EITHER'
                         or (:playerLocation = 'HOME' and l2.team = m.homeTeam)
                         or (:playerLocation = 'AWAY' and l2.team = m.awayTeam))))
            """)
    long countSearch(@Param("source") Source source, @Param("season") String season,
                     @Param("competition") String competition,
                     @Param("fromDate") java.time.LocalDate fromDate,
                     @Param("toDate") java.time.LocalDate toDate,
                     @Param("playerId") UUID playerId,
                     @Param("playerLocation") String playerLocation,
                     @Param("playerNameF0") String playerNameF0, @Param("playerNameF1") String playerNameF1,
                     @Param("playerNameF2") String playerNameF2, @Param("playerNameF3") String playerNameF3,
                     @Param("playerNameF4") String playerNameF4,
                     @Param("clubNameF0") String clubNameF0, @Param("clubNameF1") String clubNameF1,
                     @Param("clubNameF2") String clubNameF2, @Param("clubNameF3") String clubNameF3,
                     @Param("clubNameF4") String clubNameF4, @Param("status") MatchStatus status);

    @Query("""
            select m from MatchJPA m
            where m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
              and (:clubNameF0 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%'))
                   or exists (select l0.id from LineupJPA l0 join l0.player p0 where l0.match = m and lower(p0.name) like lower(concat('%', :clubNameF0, '%'))))
              and (:clubNameF1 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF1, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF1, '%'))
                   or exists (select l1.id from LineupJPA l1 join l1.player p1 where l1.match = m and lower(p1.name) like lower(concat('%', :clubNameF1, '%'))))
              and (:clubNameF2 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF2, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF2, '%'))
                   or exists (select l2.id from LineupJPA l2 join l2.player p2 where l2.match = m and lower(p2.name) like lower(concat('%', :clubNameF2, '%'))))
              and (:clubNameF3 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF3, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF3, '%'))
                   or exists (select l3.id from LineupJPA l3 join l3.player p3 where l3.match = m and lower(p3.name) like lower(concat('%', :clubNameF3, '%'))))
              and (:clubNameF4 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF4, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF4, '%'))
                   or exists (select l4.id from LineupJPA l4 join l4.player p4 where l4.match = m and lower(p4.name) like lower(concat('%', :clubNameF4, '%'))))
            order by case when m.matchDate is null then 1 else 0 end asc,
                     m.matchDate desc,
                     case when m.matchTime is null then 1 else 0 end asc,
                     m.matchTime desc,
                     m.id asc
            """)
    List<MatchJPA> searchByFragmentsInName(@Param("clubNameF0") String clubNameF0, @Param("clubNameF1") String clubNameF1,
                          @Param("clubNameF2") String clubNameF2, @Param("clubNameF3") String clubNameF3,
                          @Param("clubNameF4") String clubNameF4, Pageable pageable);

    @Query("""
            select m from MatchJPA m
            where m.source = :source
              and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            order by m.matchDate desc, m.id asc
            """)
    List<MatchJPA> findAllBySource(@Param("source") Source source);

    @Query("select distinct m.season from MatchJPA m where m.source = :source and m.season is not null order by m.season desc")
    List<String> findAllSeasonsBySource(@Param("source") Source source);

    @Query("select distinct m.competition from MatchJPA m where m.source = :source and m.season = :season and m.competition is not null order by m.competition asc")
    List<String> findAllCompetitionsBySourceAndSeason(@Param("source") Source source, @Param("season") String season);

    @Query("""
            select distinct m.season from MatchJPA m
            where m.season is not null
              and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            order by m.season desc
            """)
    List<String> findAllSeasons();

    @Query("""
            select count(m) from MatchJPA m
            where m.season = :season
              and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            """)
    long countBySeason(@Param("season") String season);

    @Query("""
            select count(m) from MatchJPA m
            where m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            """)
    long countAllPlayed();

    @Query("""
            select distinct m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            where homeTeam.id in :teamIds or awayTeam.id in :teamIds
            order by m.season asc, m.competition asc, m.id asc
            """)
    List<MatchJPA> findAllByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    @Query("""
            select distinct m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            where (homeTeam.id in :teamIds or awayTeam.id in :teamIds)
              and m.source = :source
            order by m.season asc, m.competition asc, m.id asc
            """)
    List<MatchJPA> findAllByTeamIdsAndSource(
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("source") Source source);

    @Query("""
            select distinct m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            where (homeTeam.id in :teamIds or awayTeam.id in :teamIds)
              and m.source = :source
              and m.season = :season
              and m.competition = :competition
            order by m.round asc, m.id asc
            """)
    List<MatchJPA> findAllByTeamIdsAndSourceAndSeasonAndCompetition(
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("source") Source source,
            @Param("season") String season,
            @Param("competition") String competition);

    @Query("""
            select m from MatchJPA m
            where m.competition = :competition and m.season = :season
              and (:groupNumber is null and m.groupNumber is null or m.groupNumber = :groupNumber)
              and m.round = :round
              and (:phase is null and m.phase is null or m.phase = :phase)
              and m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId
            """)
    Optional<MatchJPA> findByCompetitionAndSeasonAndGroupNumberAndRoundAndPhaseAndHomeTeam_IdAndAwayTeam_Id(
            @Param("competition") String competition,
            @Param("season") String season,
            @Param("groupNumber") Integer groupNumber,
            @Param("round") Integer round,
            @Param("phase") String phase,
            @Param("homeTeamId") UUID homeTeamId,
            @Param("awayTeamId") UUID awayTeamId);

    /**
     * FEAT-00078 backfill candidates: PLAYED matches, scoped by source and season, with no winner,
     * no non-zero header games/sets won, and no game carrying a result of its own (a winner, a
     * non-zero set count, or any set score row). A {@code not_played} game that still names a winner
     * (a walkover) counts as a result, so its match is excluded.
     */
    @Query("""
            select new org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.ScheduledMatchBackfillCandidateProjection(
                m.id, m.competition, m.groupNumber, m.round, m.phase, m.matchDate, m.homeTeam.id, m.awayTeam.id,
                (select count(g0) from GameJPA g0 where g0.match = m),
                (select count(l0) from LineupJPA l0 where l0.match = m))
            from MatchJPA m
            where m.source = :source and m.season = :season
              and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
              and m.winnerTeam is null
              and (m.homeGamesWon is null or m.homeGamesWon = 0)
              and (m.awayGamesWon is null or m.awayGamesWon = 0)
              and (m.homeSetsWon is null or m.homeSetsWon = 0)
              and (m.awaySetsWon is null or m.awaySetsWon = 0)
              and not exists (
                  select 1 from GameJPA g
                  where g.match = m
                    and (g.winner is not null
                         or coalesce(g.homeSetsWon, 0) > 0
                         or coalesce(g.awaySetsWon, 0) > 0
                         or exists (select 1 from SetScoreJPA s where s.game = g))
              )
            order by m.competition asc, m.groupNumber asc, m.round asc, m.id asc
            """)
    List<ScheduledMatchBackfillCandidateProjection> findScheduledBackfillCandidates(
            @Param("source") Source source, @Param("season") String season);

    /**
     * FEAT-00084 jornada progress counts: how many matches each round of a competition, group and
     * phase holds per status, scoped by source and season. Only the grouped rows are read here; the
     * current-round / last-complete-round rule lives in the domain {@code RoundProgressCalculator}.
     * Served by {@code idx_match_source_season_competition_status}.
     */
    @Query("""
            select new org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.RoundStatusCountProjection(
                m.competition, m.groupNumber, m.phase, m.round, m.status, count(m))
            from MatchJPA m
            where m.source = :source and m.season = :season
            group by m.competition, m.groupNumber, m.phase, m.round, m.status
            """)
    List<RoundStatusCountProjection> countByRoundAndStatus(
            @Param("source") Source source, @Param("season") String season);

    /**
     * FEAT-00102. Slim calendar projection of every match of one source and season, regardless of
     * status. No joins or fetches; served by the leading columns of
     * {@code idx_match_source_season_competition_status}.
     */
    @Query("""
            select new org.cttelsamicsterrassa.data.core.repository.jpa.match.impl.MatchCalendarEntryProjection(
                m.id, m.competition, m.groupNumber, m.phase, m.round, m.status, m.matchDate)
            from MatchJPA m
            where m.source = :source and m.season = :season
            """)
    List<MatchCalendarEntryProjection> findCalendarEntries(
            @Param("source") Source source, @Param("season") String season);

    /**
     * Same candidate rule, restricted to {@code matchIds}. Used by {@code markScheduled} to re-check
     * every id inside the write transaction before mutating anything.
     */
    @Query("""
            select m.id from MatchJPA m
            where m.source = :source and m.season = :season and m.id in :matchIds
              and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
              and m.winnerTeam is null
              and (m.homeGamesWon is null or m.homeGamesWon = 0)
              and (m.awayGamesWon is null or m.awayGamesWon = 0)
              and (m.homeSetsWon is null or m.homeSetsWon = 0)
              and (m.awaySetsWon is null or m.awaySetsWon = 0)
              and not exists (
                  select 1 from GameJPA g
                  where g.match = m
                    and (g.winner is not null
                         or coalesce(g.homeSetsWon, 0) > 0
                         or coalesce(g.awaySetsWon, 0) > 0
                         or exists (select 1 from SetScoreJPA s where s.game = g))
              )
            """)
    List<UUID> findScheduledBackfillCandidateIds(
            @Param("source") Source source, @Param("season") String season, @Param("matchIds") Collection<UUID> matchIds);

    @Modifying(clearAutomatically = true)
    @Query("""
            update MatchJPA m
               set m.homeGamesWon = null, m.awayGamesWon = null, m.homeSetsWon = null, m.awaySetsWon = null,
                   m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.SCHEDULED,
                   m.sourceChecksum = null
             where m.id in :matchIds
               and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            """)
    int markScheduledByIds(@Param("matchIds") Collection<UUID> matchIds);

    /**
     * FEAT-00089: records only the source content checksum of a PLAYED match. The status guard is
     * part of the update so a SCHEDULED match can never receive a checksum here; the caller
     * distinguishes "not found" from "not played" by the returned row count.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update MatchJPA m
               set m.sourceChecksum = :sourceChecksum
             where m.id = :matchId
               and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.PLAYED
            """)
    int recordSourceChecksum(@Param("matchId") UUID matchId, @Param("sourceChecksum") String sourceChecksum);

    /**
     * FEAT-00080 reschedule: rewrites only the schedule columns of a SCHEDULED match. The status
     * guard is part of the update so a PLAYED match can never be rescheduled here; the caller
     * distinguishes "not found" from "not scheduled" by the returned row count.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update MatchJPA m
               set m.matchDate = :matchDate, m.matchTime = :matchTime, m.city = :city, m.venue = :venue,
                   m.refereeName = :refereeName, m.refereeLicense = :refereeLicense
             where m.id = :matchId
               and m.status = org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus.SCHEDULED
            """)
    int updateScheduleOfScheduledMatch(@Param("matchId") UUID matchId,
                                       @Param("matchDate") java.time.LocalDate matchDate,
                                       @Param("matchTime") java.time.LocalTime matchTime,
                                       @Param("city") String city,
                                       @Param("venue") String venue,
                                       @Param("refereeName") String refereeName,
                                       @Param("refereeLicense") String refereeLicense);
}
