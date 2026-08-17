package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubSeasonJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubSeasonRepositoryHelper extends JpaRepository<ClubSeasonJPA, UUID> {

    Optional<ClubSeasonJPA> findClubSeasonByNameAndSeasonAndSource(String name, String season, Source source);

    List<ClubSeasonJPA> findAllByNameContainingIgnoreCase(String name);
    List<ClubSeasonJPA> findAllByNameContainingIgnoreCaseAndSeason(String name, String season);
    List<ClubSeasonJPA> findAllByNameContainingIgnoreCaseAndSeasonAndSource(String name, String season, Source source);
}
