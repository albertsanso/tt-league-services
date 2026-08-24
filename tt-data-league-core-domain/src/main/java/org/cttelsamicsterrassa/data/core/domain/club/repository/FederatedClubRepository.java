package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FederatedClubRepository {
    Optional<FederatedClub> findFederatedClubById(UUID id);
    Optional<FederatedClub> findFederatedClubBySourceAndName(ImportSource source, String name);

    default List<FederatedClub> findAllFederatedClubsBySource(ImportSource source) {
        throw new UnsupportedOperationException("Complete source inventory is not supported");
    }

    /**
     * Returns every source-specific record attached to one canonical club.
     */
    List<FederatedClub> findAllFederatedClubsByClubId(UUID clubId);

    List<FederatedClub> findAllFederatedClubsByFragmentsInName(List<String> fragments);
    List<FederatedClub> findAllFederatedClubsBySourceAndFragmentsInName(
            ImportSource source, List<String> fragments);

    void saveFederatedClub(FederatedClub club);
    void deleteFederatedClubById(UUID id);
}
