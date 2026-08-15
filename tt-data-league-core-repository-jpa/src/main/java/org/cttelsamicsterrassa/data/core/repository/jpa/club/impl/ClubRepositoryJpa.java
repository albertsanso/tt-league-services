package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubJPAToClubMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubToClubJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class ClubRepositoryJpa implements ClubRepository {

    private final ClubRepositoryHelper clubRepositoryHelper;
    private final ClubJPAToClubMapper clubJPAToClubMapper;
    private final ClubToClubJPAMapper clubToClubJPAMapper;

    @Override
    public Optional<Club> findClubById(UUID id) {
        return clubRepositoryHelper.findById(id).map(clubJPAToClubMapper);
    }

    @Override
    public Optional<Club> findClubByName(String name) {
        return clubRepositoryHelper.findByName(name).map(clubJPAToClubMapper);
    }

    @Override
    public Optional<Club> findClubBySourceAndName(ImportSource source, String name) {
        Source jpaSource = source != null ? Source.valueOf(source.name()) : null;
        return clubRepositoryHelper.findFirstBySourceAndName(jpaSource, name).map(clubJPAToClubMapper);
    }

    @Override
    public void saveClub(Club club) {
        clubRepositoryHelper.save(clubToClubJPAMapper.apply(club));
    }

    @Override
    public void updateClub(Club club) {
        clubRepositoryHelper.save(clubToClubJPAMapper.apply(club));
    }

    @Override
    public void deleteClubById(UUID id) {
        clubRepositoryHelper.deleteById(id);
    }

    @Override
    public void deleteClubByName(String name) {
        clubRepositoryHelper.findByName(name)
                .ifPresent(clubRepositoryHelper::delete);
    }

    @Override
    public List<Club> findAllClubs() {
        return clubRepositoryHelper.findAll()
                .stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    @Override
    public List<Club> findAllClubsBySimilarName(String name) {
        return clubRepositoryHelper.findAllByNameContainingIgnoreCase(name)
                .stream()
                .map(clubJPAToClubMapper)
                .toList();
    }
}
