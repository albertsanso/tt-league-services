package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubSeasonRepository {
    Optional<ClubSeason> findClubSeasonById(UUID id);
    Optional<ClubSeason> findClubSeasonByNameAndSeasonAndSource(String name, Season season, ImportSource source);

    List<ClubSeason> findAllClubSeasonsBySimilarName(String name);
    List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeason(String name, Season season);
    List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeasonAndSource(String name, Season season, ImportSource source);

    void saveClubSeason(ClubSeason clubSeason);
    void deleteClubSeasonById(UUID id);
}
