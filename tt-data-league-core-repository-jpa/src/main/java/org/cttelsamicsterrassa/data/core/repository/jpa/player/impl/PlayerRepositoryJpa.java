package org.cttelsamicsterrassa.data.core.repository.jpa.player.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.repository.jpa.club.model.ClubJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerJPAToPlayerMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.mapper.PlayerToPlayerJPAMapper;
import org.cttelsamicsterrassa.data.core.repository.jpa.player.model.PlayerJPA;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Component
@AllArgsConstructor
public class PlayerRepositoryJpa implements PlayerRepository {

    private final PlayerRepositoryHelper playerRepositoryHelper;
    private final PlayerJPAToPlayerMapper playerJPAToPlayerMapper;
    private final PlayerToPlayerJPAMapper playerToPlayerJPAMapper;

    @Override
    public Optional<Player> findPlayerById(UUID id) {
        return playerRepositoryHelper.findById(id).map(playerJPAToPlayerMapper);
    }

    @Override
    public Optional<Player> findPlayerByName(String name) {
        return playerRepositoryHelper.findByName(name).map(playerJPAToPlayerMapper);
    }

    @Override
    public Optional<Player> findPlayerBySourceAndName(ImportSource source, String name) {
        Source jpaSource = source != null ? Source.valueOf(source.name()) : null;
        return playerRepositoryHelper.findFirstBySourceAndName(jpaSource, name).map(playerJPAToPlayerMapper);
    }

    @Override
    public void savePlayer(Player player) {
        playerRepositoryHelper.save(playerToPlayerJPAMapper.apply(player));
    }

    @Override
    public void deletePlayerById(UUID id) {
        playerRepositoryHelper.deleteById(id);
    }

    @Override
    public List<Player> findAllPlayersByFragmentsInName(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }

        Specification<PlayerJPA> specification =
                (root, query, criteriaBuilder) ->
                        createNameFragmentsPredicate(root, criteriaBuilder, fragments, false);

        List<PlayerJPA> players = playerRepositoryHelper.findAll(specification, Sort.by("name"));
        return players.stream()
                .map(playerJPAToPlayerMapper)
                .toList();
    }

    private Predicate createNameFragmentsPredicate(
            Root<PlayerJPA> root,
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
