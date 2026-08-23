package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;

import java.util.Objects;

/**
 * Resolves canonical identities by exact display name only.
 */
public class CanonicalClubResolver {
    private final ClubRepository clubRepository;

    public CanonicalClubResolver(ClubRepository clubRepository) {
        this.clubRepository = Objects.requireNonNull(clubRepository, "clubRepository");
    }

    public Club resolveOrCreate(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName");
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> {
                    Club club = Club.createNew(canonicalName);
                    clubRepository.saveClub(club);
                    return club;
                });
    }

    public Club findOrCreateForReport(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName");
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> Club.createNew(canonicalName));
    }
}
