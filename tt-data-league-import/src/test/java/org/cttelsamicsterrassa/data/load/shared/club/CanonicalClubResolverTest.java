package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalClubResolverTest {

    @Test
    void reusesOnlyTheExactCanonicalDisplayName() {
        InMemoryClubRepository repository = new InMemoryClubRepository();
        CanonicalClubResolver resolver = new CanonicalClubResolver(repository);

        Club first = resolver.resolveOrCreate("Club A");
        Club reused = resolver.resolveOrCreate("Club A");
        Club differentlySpelled = resolver.resolveOrCreate("club a");

        assertEquals(first.getId(), reused.getId());
        assertEquals(2, repository.clubs.size());
        assertEquals("club a", differentlySpelled.getName());
    }

    @Test
    void reportResolutionDoesNotPersistNewCanonicalClubs() {
        InMemoryClubRepository repository = new InMemoryClubRepository();
        CanonicalClubResolver resolver = new CanonicalClubResolver(repository);

        Club reported = resolver.findOrCreateForReport("Club A");

        assertEquals("Club A", reported.getName());
        assertEquals(0, repository.clubs.size());
    }

    private static final class InMemoryClubRepository implements ClubRepository {
        private final Map<UUID, Club> clubs = new LinkedHashMap<>();

        @Override
        public Optional<Club> findClubById(UUID id) {
            return Optional.ofNullable(clubs.get(id));
        }

        @Override
        public Optional<Club> findClubByExactName(String name) {
            return clubs.values().stream().filter(club -> club.getName().equals(name)).findFirst();
        }

        @Override
        public void saveClub(Club club) {
            clubs.put(club.getId(), club);
        }

        @Override
        public void deleteClubById(UUID id) {
            clubs.remove(id);
        }
    }
}
