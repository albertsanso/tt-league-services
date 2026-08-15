package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubRepository {
    Optional<Club> findClubById(UUID id);
    Optional<Club> findClubByName(String name);

    /**
     * Finds a club by the key its source system identifies it with. For RFETM that is the federation
     * team id where the export carries one, or a key derived from the team name scoped to its season
     * and competition where it does not. This is the only reliable lookup for imported data - club
     * names drift across seasons and are shared between the A/B/C teams of one club, so an unscoped
     * name never serves.
     */
    /**
     * Finds a club by name, scoped to one federation. This is the only lookup available for sources
     * that carry no team id (BCNESA); it is scoped by {@link ImportSource} because the two
     * federations' club names are not the same namespace.
     */
    Optional<Club> findClubBySourceAndName(ImportSource source, String name);

    void saveClub(Club club);
    void updateClub(Club club);
    void deleteClubById(UUID id);
    void deleteClubByName(String name);
    List<Club> findAllClubs();
    List<Club> findAllClubsBySimilarName(String name);
}
