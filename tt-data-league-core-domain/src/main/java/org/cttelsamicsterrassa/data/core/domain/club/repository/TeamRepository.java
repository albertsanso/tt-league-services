package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository {
    Optional<Team> findTeamById(UUID id);
    Optional<Team> findTeamByNameAndSeasonAndSource(String name, Season season, ImportSource source);
    Optional<Team> findTeamByFederatedClubAndSeason(UUID federatedClubId, Season season);

    /**
     * Returns every season registration belonging to the canonical club.
     */
    List<Team> findAllTeamsByFederatedClubId(UUID federatedClubId);

    List<Team> findAllTeamsBySource(ImportSource source);
    List<Team> findAllTeamsBySimilarName(String name);
    List<Team> findAllTeamsBySimilarNameAndSeason(String name, Season season);
    List<Team> findAllTeamsBySimilarNameAndSeasonAndSource(String name, Season season, ImportSource source);

    void saveTeam(Team team);
    default void saveTeams(java.util.Collection<Team> teams) {
        teams.forEach(this::saveTeam);
    }
    void deleteTeamById(UUID id);

    /**
     * Returns the number of distinct federated clubs with a team registered for the given season,
     * across every source. Used as the community-wide "active this season" club count.
     */
    default long countDistinctFederatedClubsBySeason(Season season) {
        return 0;
    }
}
