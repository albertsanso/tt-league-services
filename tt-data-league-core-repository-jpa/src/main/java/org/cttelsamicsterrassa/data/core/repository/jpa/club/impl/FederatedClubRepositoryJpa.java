package org.cttelsamicsterrassa.data.core.repository.jpa.club.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.FederatedClubJPAToFederatedClubMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.mapper.FederatedClubToFederatedClubJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.FederatedClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class FederatedClubRepositoryJpa implements FederatedClubRepository {

    private final FederatedClubRepositoryHelper clubRepositoryHelper;
    private final FederatedClubJPAToFederatedClubMapper clubJPAToClubMapper;
    private final FederatedClubToFederatedClubJPAMapper clubToFederatedClubJPAMapper;

    @Override
    public Optional<FederatedClub> findFederatedClubById(UUID id) {
        return clubRepositoryHelper.findById(id).map(clubJPAToClubMapper);
    }

    @Override
    public Optional<FederatedClub> findFederatedClubBySourceAndName(ImportSource source, String name) {
        Source jpaSource = Source.valueOf(Objects.requireNonNull(source, "source must not be null").name());
        List<FederatedClubJPA> matches = clubRepositoryHelper.findAllBySourceAndName(jpaSource, name);
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple federated clubs found for source and name: " + source + ", " + name);
        }
        return matches.stream().findFirst().map(clubJPAToClubMapper);
    }

    @Override
    public List<FederatedClub> findAllFederatedClubsBySource(ImportSource source) {
        Source jpaSource = Source.valueOf(Objects.requireNonNull(source, "source must not be null").name());
        return clubRepositoryHelper.findAllBySource(jpaSource, Sort.by("name", "id")).stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    @Override
    public List<FederatedClub> findAllFederatedClubsByFragmentsInName(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Specification<FederatedClubJPA> clubSpec =
            (root, query, criteriaBuilder) ->
                    createNameFragmentsPredicate(root, criteriaBuilder, fragments, false);

        List<FederatedClubJPA> clubJPAs = clubRepositoryHelper.findAll(
                clubSpec, Sort.by("name", "source", "id"));
        return clubJPAs.stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    @Override
    public List<FederatedClub> findAllFederatedClubsBySourceAndFragmentsInName(
            ImportSource source, List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Source jpaSource = Source.valueOf(Objects.requireNonNull(source, "source must not be null").name());

        Specification<FederatedClubJPA> clubSpec =
            (root, query, criteriaBuilder) -> {
                return criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("source"), jpaSource),
                        createNameFragmentsPredicate(root, criteriaBuilder, fragments, false));
            };

        List<FederatedClubJPA> clubJPAs = clubRepositoryHelper.findAll(
                clubSpec, Sort.by("name", "source", "id"));
        return clubJPAs.stream()
                .map(clubJPAToClubMapper)
                .toList();
    }

    private Predicate createNameFragmentsPredicate(
            Root<FederatedClubJPA> root,
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
    public void saveFederatedClub(FederatedClub club) {
        clubRepositoryHelper.save(clubToFederatedClubJPAMapper.apply(club));
    }

    @Override
    public void deleteFederatedClubById(UUID id) {
        clubRepositoryHelper.deleteById(id);
    }

}
