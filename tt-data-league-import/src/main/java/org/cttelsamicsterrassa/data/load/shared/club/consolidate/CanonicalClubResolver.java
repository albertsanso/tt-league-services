package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Comparator;
import java.util.Optional;

public class CanonicalClubResolver {

    private final ClubRepository clubRepository;
    private final ClubNameMatcher matcher;

    public CanonicalClubResolver(ClubRepository clubRepository) {
        this(clubRepository, new ClubNameNormalizer());
    }

    CanonicalClubResolver(ClubRepository clubRepository, ClubNameNormalizer normalizer) {
        this.clubRepository = clubRepository;
        this.matcher = new ClubNameMatcher(normalizer);
    }

    public Club resolveOrCreate(String canonicalName) {
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> {
                    Club created = Club.createNew(canonicalName);
                    clubRepository.saveClub(created);
                    return created;
                });
    }

    public Optional<Club> findExisting(ImportSource source, String canonicalName) {
        return clubRepository.findAllClubs().stream()
                .map(club -> new Candidate(club, matcher.compare(source, canonicalName, club.getName())))
                .filter(candidate -> candidate.comparison().exact() || candidate.comparison().fuzzyCandidate())
                .sorted(Comparator.comparingDouble((Candidate candidate) -> candidate.comparison().score()).reversed()
                        .thenComparing(candidate -> candidate.club().getName())
                        .thenComparing(candidate -> candidate.club().getId().toString()))
                .map(Candidate::club)
                .findFirst();
    }

    public Club resolveOrCreate(ImportSource source, String canonicalName) {
        return findExisting(source, canonicalName).orElseGet(() -> {
            Club created = Club.createNew(canonicalName);
            clubRepository.saveClub(created);
            return created;
        });
    }

    public Club findOrCreateForReport(String canonicalName) {
        return clubRepository.findClubByExactName(canonicalName)
                .orElseGet(() -> Club.createExisting(java.util.UUID.randomUUID(), canonicalName));
    }

    public Club findOrCreateForReport(ImportSource source, String canonicalName) {
        return findExisting(source, canonicalName)
                .orElseGet(() -> Club.createExisting(java.util.UUID.randomUUID(), canonicalName));
    }

    private record Candidate(Club club, ClubNameComparison comparison) {
    }
}
