package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepositoryHelper extends JpaRepository<MatchJPA, UUID> {

    Optional<MatchJPA> findByExternalId(String externalId);

    @Query("""
            select distinct m from MatchJPA m
            join fetch m.homeTeam homeTeam
            left join fetch homeTeam.club
            join fetch m.awayTeam awayTeam
            left join fetch awayTeam.club
            left join fetch m.winnerTeam winnerTeam
            left join fetch winnerTeam.club
            where homeTeam.id in :teamIds or awayTeam.id in :teamIds
            order by m.season asc, m.competition asc, m.id asc
            """)
    List<MatchJPA> findAllByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    Optional<MatchJPA> findByCompetitionAndSeasonAndGroupNumberAndRoundAndHomeTeam_IdAndAwayTeam_Id(
            String competition,
            String season,
            Integer groupNumber,
            Integer round,
            UUID homeTeamId,
            UUID awayTeamId);
}
