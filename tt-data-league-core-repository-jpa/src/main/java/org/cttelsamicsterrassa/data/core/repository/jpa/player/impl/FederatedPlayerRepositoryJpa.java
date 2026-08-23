package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.FederatedPlayerJPAToFederatedPlayerMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.FederatedPlayerToFederatedPlayerJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.FederatedPlayerJPA;
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
public class FederatedPlayerRepositoryJpa implements FederatedPlayerRepository {

    private final FederatedPlayerRepositoryHelper federatedPlayerRepositoryHelper;
    private final FederatedPlayerJPAToFederatedPlayerMapper federatedPlayerJPAToFederatedPlayerMapper;
    private final FederatedPlayerToFederatedPlayerJPAMapper federatedPlayerToFederatedPlayerJPAMapper;

    @Override
    public Optional<FederatedPlayer> findFederatedPlayerById(UUID id) {
        return federatedPlayerRepositoryHelper.findById(id).map(federatedPlayerJPAToFederatedPlayerMapper);
    }

    @Override
    public Optional<FederatedPlayer> findFederatedPlayerBySourceAndName(ImportSource source, String name) {
        Source jpaSource = Source.valueOf(Objects.requireNonNull(source, "source must not be null").name());
        List<FederatedPlayerJPA> matches = federatedPlayerRepositoryHelper.findAllBySourceAndName(jpaSource, name);
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple federated players found for source and name: " + source + ", " + name);
        }
        return matches.stream().map(federatedPlayerJPAToFederatedPlayerMapper).findFirst();
    }

    @Override
    public void saveFederatedPlayer(FederatedPlayer player) {
        federatedPlayerRepositoryHelper.save(federatedPlayerToFederatedPlayerJPAMapper.apply(player));
    }

    @Override
    public void deleteFederatedPlayerById(UUID id) {
        federatedPlayerRepositoryHelper.deleteById(id);
    }

    @Override
    public List<FederatedPlayer> findAllFederatedPlayersByFragmentsInName(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Specification<FederatedPlayerJPA> specification =
                (root, query, criteriaBuilder) ->
                        createNameFragmentsPredicate(root, criteriaBuilder, fragments, false);

        List<FederatedPlayerJPA> players = federatedPlayerRepositoryHelper.findAll(specification, Sort.by("name"));
        return players.stream()
                .map(federatedPlayerJPAToFederatedPlayerMapper)
                .toList();
    }

    private Predicate createNameFragmentsPredicate(
            Root<FederatedPlayerJPA> root,
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
}
