package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.FederatedClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FederatedClubRepositoryHelper extends JpaRepository<FederatedClubJPA, UUID>, JpaSpecificationExecutor<FederatedClubJPA> {
    List<FederatedClubJPA> findAllBySourceAndName(Source source, String name);
    List<FederatedClubJPA> findAllBySource(Source source, Sort sort);
    List<FederatedClubJPA> findAllByNameContainingIgnoreCase(String name);
    List<FederatedClubJPA> findAllBySourceAndNameContainingIgnoreCase(Source source, String name);
}
