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

    Optional<ClubSeason> findClubSeasonByClubAndSeasonAndSource(UUID clubId, Season season, String source);
    Optional<ClubSeason> findClubSeasonByClubAndNameAndSeasonAndSource( UUID clubId, String name, Season season, String source);

    List<ClubSeason> findClubSeasonByClubAndSeason(UUID clubId, Season season);
    List<ClubSeason> findAllClubSeasonsBySimilarName(String name);
    List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeason(String name, Season season);
    List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeasonAndSoure(String name, Season season, String source);

    void saveClubSeason(ClubSeason clubSeason);
    void deleteClubSeasonById(UUID id);
}
