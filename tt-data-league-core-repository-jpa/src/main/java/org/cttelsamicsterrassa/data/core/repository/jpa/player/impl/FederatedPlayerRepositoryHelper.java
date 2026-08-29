package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.FederatedPlayerJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface FederatedPlayerRepositoryHelper extends JpaRepository<FederatedPlayerJPA, UUID>, JpaSpecificationExecutor<FederatedPlayerJPA> {
    List<FederatedPlayerJPA> findAllBySourceAndName(Source source, String name);
    List<FederatedPlayerJPA> findAllBySourceAndLicenseId(Source source, String licenseId);
    List<FederatedPlayerJPA> findAllByPlayer_IdOrderBySourceAscNameAscIdAsc(UUID playerId);
}
