package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerSeasonJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PlayerSeasonRepositoryHelper extends JpaRepository<PlayerSeasonJPA, UUID> {
    Optional<PlayerSeasonJPA> findBySourceAndLicenseIdAndSeason(Source source, String licenseId, String season);
    List<PlayerSeasonJPA> findAllBySource(Source source);

    @Query("""
            select distinct player from LineupJPA l
            join l.player player
            where l.team.id in :teamIds
              and l.team.source = :source
              and player.source = :source
            order by player.name asc, player.id asc
            """)
    List<PlayerSeasonJPA> findAllByTeamIdsAndSource(
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("source") Source source);

    @Query("""
            select player.id as playerSeasonId, matchRecord.competition as competition
            from LineupJPA lineup
            join lineup.player player
            join lineup.team team
            join lineup.match matchRecord
            where team.id in :teamIds
              and team.source = :source
              and player.source = :source
              and matchRecord.source = :source
              and matchRecord.season = player.season
              and matchRecord.competition is not null
            order by player.id asc, matchRecord.competition asc
            """)
    List<PlayerSeasonCompetitionProjection> findAllPlayerSeasonCompetitionsByTeamIdsAndSource(
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("source") Source source);

    interface PlayerSeasonCompetitionProjection {
        UUID getPlayerSeasonId();

        String getCompetition();
    }
}
