package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RunUnitJpaRepository extends JpaRepository<RunUnitEntity, UUID> {

    List<RunUnitEntity> findByRunIdOrderByOrdinalAsc(UUID runId);

    List<RunUnitEntity> findByRunIdInOrderByRunIdAscOrdinalAsc(Collection<UUID> runIds);

    /**
     * The ids of the newest {@code perKey} finished units (skipped ones excluded) of each unit key of the source. A
     * window function, because the history grows with every run and only a few rows per key are wanted.
     */
    @Query(nativeQuery = true, value = "SELECT t.id FROM ("
            + "SELECT u.id, row_number() OVER (PARTITION BY u.unit_key ORDER BY u.finished_at DESC, u.id) AS rn "
            + "FROM pipeline.pipeline_unit u JOIN pipeline.pipeline_run r ON r.id = u.run_id "
            + "WHERE r.source = :source AND u.status IN ('NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED')) t "
            + "WHERE t.rn <= :perKey")
    List<UUID> findNewestFinishedIds(@Param("source") String source, @Param("perKey") int perKey);

    @Query(nativeQuery = true, value = "SELECT t.id FROM ("
            + "SELECT u.id, row_number() OVER (PARTITION BY u.unit_key ORDER BY u.finished_at DESC, u.id) AS rn "
            + "FROM pipeline.pipeline_unit u JOIN pipeline.pipeline_run r ON r.id = u.run_id "
            + "WHERE r.source = :source AND u.status IN ('NO_CHANGES', 'SUCCEEDED', 'PARTIAL', 'FAILED') "
            + "AND u.unit_key IN (:unitKeys)) t "
            + "WHERE t.rn <= :perKey")
    List<UUID> findNewestFinishedIdsForKeys(
            @Param("source") String source, @Param("perKey") int perKey,
            @Param("unitKeys") Collection<String> unitKeys);
}
