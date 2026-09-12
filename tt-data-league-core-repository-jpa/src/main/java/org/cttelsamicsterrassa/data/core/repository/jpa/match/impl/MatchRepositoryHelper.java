package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
            select m from MatchJPA m
            where m.source = :source and m.season = :season
              and (:competition is null or m.competition = :competition)
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:clubNameF0 = '' or
                   lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%'))
                or (:clubNameF1 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF1, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF1, '%'))))
                or (:clubNameF2 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF2, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF2, '%'))))
                or (:clubNameF3 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF3, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF3, '%'))))
                or (:clubNameF4 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF4, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF4, '%')))))
              and (:playerNameF0 = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and (lower(p.name) like lower(concat('%', :playerNameF0, '%'))
                         or (:playerNameF1 <> '' and lower(p.name) like lower(concat('%', :playerNameF1, '%')))
                         or (:playerNameF2 <> '' and lower(p.name) like lower(concat('%', :playerNameF2, '%')))
                         or (:playerNameF3 <> '' and lower(p.name) like lower(concat('%', :playerNameF3, '%')))
                         or (:playerNameF4 <> '' and lower(p.name) like lower(concat('%', :playerNameF4, '%'))))
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
                          @Param("clubNameF4") String clubNameF4, Pageable pageable);

    @Query("""
            select count(m) from MatchJPA m
            where m.source = :source and m.season = :season
              and (:competition is null or m.competition = :competition)
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:clubNameF0 = '' or
                   lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%'))
                or (:clubNameF1 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF1, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF1, '%'))))
                or (:clubNameF2 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF2, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF2, '%'))))
                or (:clubNameF3 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF3, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF3, '%'))))
                or (:clubNameF4 <> '' and (lower(m.homeTeam.name) like lower(concat('%', :clubNameF4, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF4, '%')))))
              and (:playerNameF0 = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and (lower(p.name) like lower(concat('%', :playerNameF0, '%'))
                         or (:playerNameF1 <> '' and lower(p.name) like lower(concat('%', :playerNameF1, '%')))
                         or (:playerNameF2 <> '' and lower(p.name) like lower(concat('%', :playerNameF2, '%')))
                         or (:playerNameF3 <> '' and lower(p.name) like lower(concat('%', :playerNameF3, '%')))
                         or (:playerNameF4 <> '' and lower(p.name) like lower(concat('%', :playerNameF4, '%'))))
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
                     @Param("clubNameF4") String clubNameF4);

    @Query("""
            select m from MatchJPA m
            where (:clubNameF0 = '' or lower(m.homeTeam.name) like lower(concat('%', :clubNameF0, '%')) or lower(m.awayTeam.name) like lower(concat('%', :clubNameF0, '%'))
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

    @Query("select m from MatchJPA m where m.source = :source order by m.matchDate desc, m.id asc")
    List<MatchJPA> findAllBySource(@Param("source") Source source);

    @Query("select distinct m.season from MatchJPA m where m.source = :source and m.season is not null order by m.season desc")
    List<String> findAllSeasonsBySource(@Param("source") Source source);

    @Query("select distinct m.competition from MatchJPA m where m.source = :source and m.season = :season and m.competition is not null order by m.competition asc")
    List<String> findAllCompetitionsBySourceAndSeason(@Param("source") Source source, @Param("season") String season);

    @Query("select distinct m.season from MatchJPA m where m.season is not null order by m.season desc")
    List<String> findAllSeasons();

    @Query("select count(m) from MatchJPA m where m.season = :season")
    long countBySeason(@Param("season") String season);

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
}
