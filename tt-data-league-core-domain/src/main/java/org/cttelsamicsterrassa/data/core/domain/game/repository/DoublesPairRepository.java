package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

/**
 * Persistence port for the members of a doubles pair.
 */
public interface DoublesPairRepository {
    List<DoublesPair> findDoublesPairsByGameIds(Collection<UUID> gameIds);
    void saveDoublesPairs(List<DoublesPair> doublesPairs);
}
