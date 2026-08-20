package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubJPAToClubMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.ClubToClubJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    public List<Club> findAllClubsByFragmentsInName(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Specification<ClubJPA> clubSpec =
            (root, query, criteriaBuilder) ->
                    createNameFragmentsPredicate(root, criteriaBuilder, fragments, false);

        List<ClubJPA> clubJPAs = clubRepositoryHelper.findAll(clubSpec, Sort.by("name"));
        return clubJPAs.stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    @Override
    public List<Club> findAllClubsBySourceAndFragmentsInName(ImportSource source, List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Source jpaSource = source != null ? Source.valueOf(source.name()) : null;

        Specification<ClubJPA> clubSpec =
            (root, query, criteriaBuilder) -> {
                Predicate sourcePredicate = jpaSource != null
                        ? criteriaBuilder.equal(root.get("source"), jpaSource)
                        : criteriaBuilder.conjunction();

                return criteriaBuilder.and(
                        sourcePredicate,
                        createNameFragmentsPredicate(root, criteriaBuilder, fragments, false));
            };

        List<ClubJPA> clubJPAs = clubRepositoryHelper.findAll(clubSpec, Sort.by("name"));
        return clubJPAs.stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    private Predicate createNameFragmentsPredicate(
            Root<ClubJPA> root,
            CriteriaBuilder criteriaBuilder,
            List<String> fragments,
            boolean matchAnyFragment) {
        List<Predicate> predicates = fragments.stream()
                .map(fragment ->
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%" + fragment.toLowerCase() + "%"
                        )
                ).toList();

        return matchAnyFragment
                ? criteriaBuilder.or(predicates.toArray(new Predicate[0]))
                : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }


    @Override
    public void saveClub(Club club) {
        clubRepositoryHelper.save(clubToClubJPAMapper.apply(club));
    }

    @Override
    public void deleteClubById(UUID id) {
        clubRepositoryHelper.deleteById(id);
    }

    /*
    @Override
    public List<Club> findAllClubsBySimilarName(String name) {
        return clubRepositoryHelper.findAllByNameContainingIgnoreCase(name)
                .stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    @Override
    public List<Club> findAllClubsBySimilarNameAndSource(String name, String source) {
        return clubRepositoryHelper.findAllByNameContainingIgnoreCaseAndSource(name, source)
                .stream()
                .map(clubJPAToClubMapper)
                .toList();

    }
     */
}
