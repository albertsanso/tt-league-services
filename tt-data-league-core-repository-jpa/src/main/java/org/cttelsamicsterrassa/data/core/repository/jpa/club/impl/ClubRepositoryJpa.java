package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubJPAToClubMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubToClubJPAMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
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
    public Optional<Club> findClubByExactName(String name) {
        return clubRepositoryHelper.findByName(name).map(clubJPAToClubMapper);
    }

    @Override
    public List<Club> findAllClubs() {
        return clubRepositoryHelper.findAll().stream()
                .map(clubJPAToClubMapper)
                .sorted(Comparator.comparing(Club::getName).thenComparing(club -> club.getId().toString()))
                .toList();
    }

    @Override
    public void saveClub(Club club) {
        clubRepositoryHelper.save(clubToClubJPAMapper.apply(club));
    }

    @Override
    public void deleteClubById(UUID id) {
        clubRepositoryHelper.deleteById(id);
    }
}
