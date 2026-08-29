package org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.doublespair.model.DoublesPairJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoublesPairRepositoryHelper extends JpaRepository<DoublesPairJPA, UUID> {
    @Query("""
            select distinct pair from DoublesPairJPA pair
            join fetch pair.game gameRecord
            join fetch pair.player player
            join fetch player.federatedPlayer federated
            left join fetch federated.player canonical
            where gameRecord.id in :gameIds
            order by gameRecord.id asc, pair.side asc, pair.id asc
            """)
    List<DoublesPairJPA> findAllByGameIds(@Param("gameIds") Collection<UUID> gameIds);
}
