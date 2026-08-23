package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.TeamJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepositoryHelper extends JpaRepository<TeamJPA, UUID> {

    Optional<TeamJPA> findTeamByNameAndSeasonAndSource(String name, String season, Source source);

    Optional<TeamJPA> findFirstByFederatedClub_IdAndSeason(UUID federatedClubId, String season);

    @Query("""
            select t from TeamJPA t
            left join fetch t.federatedClub
            where t.federatedClub.id = :federatedClubId
            order by t.season asc, t.name asc, t.id asc
            """)
    List<TeamJPA> findAllByFederatedClubId(@Param("federatedClubId") UUID federatedClubId);

    List<TeamJPA> findAllBySource(Source source);

    List<TeamJPA> findAllByNameContainingIgnoreCase(String name);
    List<TeamJPA> findAllByNameContainingIgnoreCaseAndSeason(String name, String season);
    List<TeamJPA> findAllByNameContainingIgnoreCaseAndSeasonAndSource(String name, String season, Source source);
}
