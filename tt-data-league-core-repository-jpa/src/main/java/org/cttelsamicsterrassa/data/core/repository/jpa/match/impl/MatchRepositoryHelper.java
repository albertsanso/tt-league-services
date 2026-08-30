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
            where m.source = :source and m.season = :season and m.competition = :competition
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:playerName = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and lower(p.name) like lower(concat('%', :playerName, '%'))
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
                          @Param("playerName") String playerName, Pageable pageable);

    @Query("""
            select count(m) from MatchJPA m
            where m.source = :source and m.season = :season and m.competition = :competition
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
              and (:playerName = '' or exists (
                  select l.id from LineupJPA l join l.player p
                  where l.match = m and p.source = :source
                    and lower(p.name) like lower(concat('%', :playerName, '%'))
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
                     @Param("playerName") String playerName);

    @Query("select m from MatchJPA m where m.source = :source order by m.matchDate desc, m.id asc")
    List<MatchJPA> findAllBySource(@Param("source") Source source);

    @Query("select distinct m.season from MatchJPA m where m.source = :source and m.season is not null order by m.season desc")
    List<String> findAllSeasonsBySource(@Param("source") Source source);

    @Query("select distinct m.competition from MatchJPA m where m.source = :source and m.season = :season and m.competition is not null order by m.competition asc")
    List<String> findAllCompetitionsBySourceAndSeason(@Param("source") Source source, @Param("season") String season);

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

    Optional<MatchJPA> findByCompetitionAndSeasonAndGroupNumberAndRoundAndHomeTeam_IdAndAwayTeam_Id(
            String competition,
            String season,
            Integer groupNumber,
            Integer round,
            UUID homeTeamId,
            UUID awayTeamId);
}
