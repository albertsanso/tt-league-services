package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.TeamJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepositoryHelper extends JpaRepository<TeamJPA, UUID> {

    Optional<TeamJPA> findTeamByNameAndSeasonAndSource(String name, String season, Source source);

    Optional<TeamJPA> findFirstByClub_IdAndSeason(UUID clubId, String season);

    List<TeamJPA> findAllBySource(Source source);

    List<TeamJPA> findAllByNameContainingIgnoreCase(String name);
    List<TeamJPA> findAllByNameContainingIgnoreCaseAndSeason(String name, String season);
    List<TeamJPA> findAllByNameContainingIgnoreCaseAndSeasonAndSource(String name, String season, Source source);
}
