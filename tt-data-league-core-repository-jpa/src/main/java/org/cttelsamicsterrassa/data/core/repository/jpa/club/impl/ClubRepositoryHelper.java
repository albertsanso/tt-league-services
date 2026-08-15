package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubRepositoryHelper extends JpaRepository<ClubJPA, UUID> {
    Optional<ClubJPA> findByName(String name);
    Optional<ClubJPA> findFirstBySourceAndName(Source source, String name);
    List<ClubJPA> findAllByNameContainingIgnoreCase(String name);
}
