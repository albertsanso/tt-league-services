package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.match.model.MatchOverdueMark;
import org.cttelsamicsterrassa.data.core.domain.match.repository.MatchOverdueMarkRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchJPA;
import org.cttelsamicsterrassa.data.core.repository.jpa.match.model.MatchOverdueMarkJPA;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for the manual overdue mark (FEAT-00092). The mark is operator input and is kept
 * separate from import data; nothing here reads or writes it except this adapter. {@code save}
 * inserts a new row or leaves an existing one untouched; {@code deleteByMatchId} reports whether a
 * row was actually removed.
 */
@Transactional
@Component
@AllArgsConstructor
public class MatchOverdueMarkRepositoryJpa implements MatchOverdueMarkRepository {

    private final MatchOverdueMarkRepositoryHelper helper;
    private final MatchRepositoryHelper matchRepositoryHelper;

    @Override
    public Optional<MatchOverdueMark> findByMatchId(UUID matchId) {
        return helper.findById(matchId).map(MatchOverdueMarkRepositoryJpa::toDomain);
    }

    @Override
    public List<MatchOverdueMark> findByMatchIds(Collection<UUID> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return List.of();
        }
        return helper.findAllById(matchIds).stream()
                .map(MatchOverdueMarkRepositoryJpa::toDomain)
                .toList();
    }

    @Override
    public void save(MatchOverdueMark mark) {
        if (helper.existsById(mark.matchId())) {
            return;
        }
        MatchJPA match = matchRepositoryHelper.getReferenceById(mark.matchId());
        helper.save(new MatchOverdueMarkJPA(match, mark.markedAt(), mark.markedBy()));
    }

    @Override
    public boolean deleteByMatchId(UUID matchId) {
        if (!helper.existsById(matchId)) {
            return false;
        }
        helper.deleteById(matchId);
        return true;
    }

    private static MatchOverdueMark toDomain(MatchOverdueMarkJPA jpa) {
        return new MatchOverdueMark(jpa.getMatchId(), jpa.getMarkedAt(), jpa.getMarkedBy());
    }
}