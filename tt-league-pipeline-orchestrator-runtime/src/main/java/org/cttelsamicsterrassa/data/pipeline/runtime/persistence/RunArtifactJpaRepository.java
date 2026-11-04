package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RunArtifactJpaRepository extends JpaRepository<RunArtifactEntity, UUID> {

    List<RunArtifactEntity> findByRunIdOrderByCreatedAtAsc(UUID runId);

    List<RunArtifactEntity> findByUnitIdOrderByCreatedAtAsc(UUID unitId);

    List<RunArtifactEntity> findByStorageKeyOrderByCreatedAtAsc(String storageKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RunArtifactEntity a set a.purgedAt = :at where a.storageKey = :key and a.purgedAt is null")
    int markPurged(@Param("key") String storageKey, @Param("at") Instant at);
}
