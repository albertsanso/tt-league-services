package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubSeasonRepository {
    Optional<ClubSeason> findClubSeasonById(UUID id);
    Optional<ClubSeason> findClubSeasonByNameAndSeason(String name, Season season);

    /**
     * Finds the season entry of a given club. This is the identity-safe lookup: within one season
     * several distinct teams share a display name, so name and season alone are ambiguous.
     */
    Optional<ClubSeason> findClubSeasonByClubAndSeason(UUID clubId, Season season);

    void saveClubSeason(ClubSeason clubSeason);
    void updateClubSeason(ClubSeason clubSeason);
    void deleteClubSeasonById(UUID id);
    void deleteClubSeasonByNameAndSeason(String name, Season season);
    List<ClubSeason> findAllClubSeasons();
    List<ClubSeason> findAllClubSeasonsBySimilarName(String name);
    List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeason(String name, Season season);
}
