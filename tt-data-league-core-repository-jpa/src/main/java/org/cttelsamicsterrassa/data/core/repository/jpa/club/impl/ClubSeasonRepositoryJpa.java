package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubSeasonJPAToClubSeasonMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubSeasonToClubSeasonJPAMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ClubSeasonRepositoryJpa implements ClubSeasonRepository {

    private final ClubSeasonRepositoryHelper clubSeasonRepositoryHelper;
    private final ClubSeasonJPAToClubSeasonMapper clubSeasonJPAToClubSeasonMapper;
    private final ClubSeasonToClubSeasonJPAMapper clubSeasonToClubSeasonJPAMapper;

    @Override
    public Optional<ClubSeason> findClubSeasonById(UUID id) {
        return clubSeasonRepositoryHelper.findById(id).map(clubSeasonJPAToClubSeasonMapper);
    }

    @Override
    public List<ClubSeason> findClubSeasonByClubAndSeason(UUID clubId, Season season) {
        return clubSeasonRepositoryHelper.findByClub_IdAndSeason(clubId, season.toString())
                .stream()
                .map(clubSeasonJPAToClubSeasonMapper)
                .toList();
    }

    @Override
    public Optional<ClubSeason> findClubSeasonByNameAndSeasonAndSource(String name, Season season, ImportSource source) {
        return clubSeasonRepositoryHelper.findClubSeasonByNameAndSeasonAndSource(name, season.toString(), source.toString())
                .map(clubSeasonJPAToClubSeasonMapper);
    }

    @Override
    public Optional<ClubSeason> findClubSeasonByClubAndSeasonAndSource(UUID clubId, Season season, String source) {
        return Optional.empty();
    }

    @Override
    public Optional<ClubSeason> findClubSeasonByClubAndNameAndSeasonAndSource(UUID clubId, String name, Season season, String source) {
        return Optional.empty();
    }

    @Override
    public void saveClubSeason(ClubSeason clubSeason) {
        clubSeasonRepositoryHelper.save(clubSeasonToClubSeasonJPAMapper.apply(clubSeason));
    }

    @Override
    public void deleteClubSeasonById(UUID id) {
        clubSeasonRepositoryHelper.deleteById(id);
    }

    @Override
    public List<ClubSeason> findAllClubSeasonsBySimilarName(String name) {
        return clubSeasonRepositoryHelper.findAllByNameContainingIgnoreCase(name)
                .stream()
                .map(clubSeasonJPAToClubSeasonMapper)
                .toList();
    }

    @Override
    public List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeason(String name, Season season) {
        return clubSeasonRepositoryHelper.findAllByNameContainingIgnoreCaseAndSeason(name, season.toString())
                .stream()
                .map(clubSeasonJPAToClubSeasonMapper)
                .toList();
    }

    @Override
    public List<ClubSeason> findAllClubSeasonsBySimilarNameAndSeasonAndSoure(String name, Season season, String source) {
        return List.of();
    }
}
