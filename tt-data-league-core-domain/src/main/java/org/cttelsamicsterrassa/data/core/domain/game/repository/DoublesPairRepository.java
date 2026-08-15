package org.cttelsamicsterrassa.data.core.domain.game.repository;

import org.cttelsamicsterrassa.data.core.domain.game.model.DoublesPair;

import java.util.List;

/**
 * Persistence port for the members of a doubles pair.
 */
public interface DoublesPairRepository {
    void saveDoublesPairs(List<DoublesPair> doublesPairs);
}
