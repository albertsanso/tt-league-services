package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

public class CanonicalClubResolver {

    private final ClubRepository clubRepository;

    public CanonicalClubResolver(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    public Club resolveOrCreate(String canonicalName) {
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> {
                    Club created = Club.createNew(canonicalName);
                    clubRepository.saveClub(created);
                    return created;
                });
    }

    public Club findOrCreateForReport(String canonicalName) {
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> Club.createExisting(java.util.UUID.randomUUID(), canonicalName));
    }
}
