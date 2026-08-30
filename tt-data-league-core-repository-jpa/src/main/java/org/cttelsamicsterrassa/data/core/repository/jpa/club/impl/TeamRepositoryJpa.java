package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.TeamJPAToTeamMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.TeamToTeamJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class TeamRepositoryJpa implements TeamRepository {

    private final TeamRepositoryHelper teamRepositoryHelper;
    private final TeamJPAToTeamMapper teamJPAToTeamMapper;
    private final TeamToTeamJPAMapper teamToTeamJPAMapper;

    @Override
    public Optional<Team> findTeamById(UUID id) {
        return teamRepositoryHelper.findById(id).map(teamJPAToTeamMapper);
    }

    @Override
    public Optional<Team> findTeamByNameAndSeasonAndSource(String name, Season season, ImportSource source) {
        return teamRepositoryHelper.findTeamByNameAndSeasonAndSource(name, season.toString(), mapFromImportSourceToSource(source))
                .map(teamJPAToTeamMapper);
    }

    @Override
    public Optional<Team> findTeamByFederatedClubAndSeason(UUID federatedClubId, Season season) {
        return teamRepositoryHelper.findFirstByFederatedClub_IdAndSeason(
                        federatedClubId, season.toString())
                .map(teamJPAToTeamMapper);
    }

    @Override
    public List<Team> findAllTeamsByFederatedClubId(UUID federatedClubId) {
        return teamRepositoryHelper.findAllByFederatedClubId(federatedClubId)
                .stream()
                .map(teamJPAToTeamMapper)
                .toList();
    }

    @Override
    public List<Team> findAllTeamsBySource(ImportSource source) {
        return teamRepositoryHelper.findAllBySource(mapFromImportSourceToSource(source))
                .stream()
                .map(teamJPAToTeamMapper)
                .toList();
    }

    @Override
    public void saveTeam(Team team) {
        teamRepositoryHelper.save(teamToTeamJPAMapper.apply(team));
    }

    @Override
    public void deleteTeamById(UUID id) {
        teamRepositoryHelper.deleteById(id);
    }

    @Override
    public List<Team> findAllTeamsBySimilarName(String name) {
        return teamRepositoryHelper.findAllByNameContainingIgnoreCase(name)
                .stream()
                .map(teamJPAToTeamMapper)
                .toList();
    }

    @Override
    public List<Team> findAllTeamsBySimilarNameAndSeason(String name, Season season) {
        return teamRepositoryHelper.findAllByNameContainingIgnoreCaseAndSeason(name, season.toString())
                .stream()
                .map(teamJPAToTeamMapper)
                .toList();
    }

    @Override
    public List<Team> findAllTeamsBySimilarNameAndSeasonAndSource(String name, Season season, ImportSource source) {
        return teamRepositoryHelper.findAllByNameContainingIgnoreCaseAndSeasonAndSource(name, season.toString(), mapFromImportSourceToSource(source))
                .stream()
                .map(teamJPAToTeamMapper)
                .toList();
    }

    @Override
    public long countDistinctFederatedClubsBySeason(Season season) {
        if (season == null) {
            return 0;
        }
        return teamRepositoryHelper.countDistinctFederatedClubsBySeason(season.toString());
    }

    private static Source mapFromImportSourceToSource(ImportSource source) {
        if (source == null) {
            return null;
        }
        return Source.valueOf(source.name());
    }
}
