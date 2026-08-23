package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;

import java.util.Optional;
import java.util.UUID;

public interface ClubRepository {
    Optional<Club> findClubById(UUID id);

    /**
     * Finds a canonical club using the exact, case-sensitive display name.
     */
    Optional<Club> findClubByExactName(String name);

    default Optional<Club> findClubByName(String name) {
        return findClubByExactName(name);
    }

    void saveClub(Club club);

    void deleteClubById(UUID id);
}
