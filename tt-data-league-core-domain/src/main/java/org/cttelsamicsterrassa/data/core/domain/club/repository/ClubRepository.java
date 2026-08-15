package org.cttelsamicsterrassa.data.core.domain.club.repository;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubRepository {
    Optional<Club> findClubById(UUID id);
    Optional<Club> findClubByName(String name);
    Optional<Club> findClubBySourceAndName(ImportSource source, String name);
    List<Club> findAllClubs();
    List<Club> findAllClubsBySimilarName(String name);

    void saveClub(Club club);
    void updateClub(Club club);
    void deleteClubById(UUID id);
    void deleteClubByName(String name);
}
