package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubSeasonRepositoryHelper extends JpaRepository<ClubSeasonJPA, UUID> {
    Optional<ClubSeasonJPA> findByNameAndSeason(String name, String season);
    Optional<ClubSeasonJPA> findByClub_IdAndSeason(UUID clubId, String season);
    List<ClubSeasonJPA> findAllByNameContainingIgnoreCase(String name);
    List<ClubSeasonJPA> findAllByNameContainingIgnoreCaseAndSeason(String name, String season);
}
