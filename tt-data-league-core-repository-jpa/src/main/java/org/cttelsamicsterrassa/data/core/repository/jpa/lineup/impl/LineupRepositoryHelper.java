package org.cttelsamicsterrassa.data.core.repository.jpa.lineup.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.lineup.model.LineupJPA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface LineupRepositoryHelper extends JpaRepository<LineupJPA, UUID> {
    List<LineupJPA> findAllByMatch_IdOrderByTeam_IdAscPositionAsc(UUID matchId);

    @Query("""
            select distinct l from LineupJPA l
            join fetch l.match matchRecord
            join fetch l.team lineupTeam
            join fetch l.player player
            left join fetch player.federatedPlayer federated
            left join fetch federated.player canonical
            where l.match.id in :matchIds
            order by l.match.id asc, l.team.id asc, l.position asc
            """)
    List<LineupJPA> findAllByMatchIds(@Param("matchIds") Collection<UUID> matchIds);
    List<LineupJPA> findAllByMatch_Id(UUID matchId);

    @Query("""
            select distinct l from LineupJPA l
            join fetch l.player player
            left join fetch player.federatedPlayer federated
            left join fetch federated.player canonical
            join fetch l.match matchRecord
            join fetch matchRecord.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch matchRecord.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch matchRecord.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            join fetch l.team lineupTeam
            left join fetch lineupTeam.federatedClub
            where player.id in :playerSeasonIds
            order by matchRecord.season asc, matchRecord.competition asc, matchRecord.round asc, l.id asc
            """)
    List<LineupJPA> findAllByPlayerSeasonIds(
            @Param("playerSeasonIds") Collection<UUID> playerSeasonIds);

    @Query("""
            select distinct l from LineupJPA l
            join fetch l.player player
            left join fetch player.federatedPlayer federated
            left join fetch federated.player canonical
            join fetch l.match matchRecord
            join fetch matchRecord.homeTeam homeTeam
            left join fetch homeTeam.federatedClub
            join fetch matchRecord.awayTeam awayTeam
            left join fetch awayTeam.federatedClub
            left join fetch matchRecord.winnerTeam winnerTeam
            left join fetch winnerTeam.federatedClub
            join fetch l.team lineupTeam
            left join fetch lineupTeam.federatedClub
            where player.id in :playerSeasonIds
            order by matchRecord.season asc, matchRecord.competition asc, matchRecord.round asc, l.id asc
            """)
    List<LineupJPA> findAllByPlayerSeasonIds(
            @Param("playerSeasonIds") Collection<UUID> playerSeasonIds,
            Pageable pageable);
}
