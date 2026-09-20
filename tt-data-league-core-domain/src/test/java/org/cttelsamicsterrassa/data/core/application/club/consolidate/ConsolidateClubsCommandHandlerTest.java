package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.albertsanso.commons.command.DomainCommandResponse;
import org.albertsanso.commons.event.DomainEvent;
import org.albertsanso.commons.event.DomainEventSubscriber;
import org.albertsanso.commons.event.EventBus;
import org.cttelsamicsterrassa.data.core.domain.club.event.ClubsConsolidatedEvent;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsolidateClubsCommandHandlerTest {

    @Test
    void mergesSelectedClubsIntoThePrimaryClub() {
        RecordingEventBus eventBus = new RecordingEventBus();
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        Club secondary = Club.createExisting(UUID.randomUUID(), "C.T.T. Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary, secondary));
        FederatedClub federatedClub = FederatedClub.createExisting(
                UUID.randomUUID(), ImportSource.RFETM, "CTT Terrassa RFETM", secondary);
        InMemoryFederatedClubs federatedClubs = new InMemoryFederatedClubs(List.of(federatedClub));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(clubs, federatedClubs, eventBus);

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), secondary.getId()), "CTT Terrassa Consolidat", primary.getId(), null, null));

        assertTrue(response.isSuccess());
        Club consolidated = (Club) response.getResponse();
        assertEquals(primary.getId(), consolidated.getId());
        assertEquals("CTT Terrassa Consolidat", consolidated.getName());
        assertTrue(clubs.findClubById(secondary.getId()).isEmpty());
        assertEquals(
                primary.getId(),
                federatedClubs.findFederatedClubById(federatedClub.getId()).orElseThrow()
                        .getClub().orElseThrow().getId());
    }

    @Test
    void failsWhenFewerThanTwoDistinctClubsAreSelected() {
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(clubs, new InMemoryFederatedClubs(List.of()), new RecordingEventBus());

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), primary.getId()), "CTT Terrassa Consolidat", primary.getId(), null, null));

        assertFalse(response.isSuccess());
    }

    @Test
    void failsWhenPrimaryClubIsNotAmongTheSelectedClubs() {
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        Club secondary = Club.createExisting(UUID.randomUUID(), "C.T.T. Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary, secondary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(clubs, new InMemoryFederatedClubs(List.of()), new RecordingEventBus());

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), secondary.getId()), "CTT Terrassa Consolidat", UUID.randomUUID(), null, null));

        assertFalse(response.isSuccess());
        assertEquals("Primary club must be one of the selected clubs", response.getResponse());
    }

    @Test
    void failsWhenAClubIsNotFound() {
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        UUID missingId = UUID.randomUUID();
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(clubs, new InMemoryFederatedClubs(List.of()), new RecordingEventBus());

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), missingId), "CTT Terrassa Consolidat", primary.getId(), null, null));

        assertFalse(response.isSuccess());
        assertEquals("Club not found: " + missingId, response.getResponse());
    }

    @Test
    void failsWhenCanonicalNameIsTooShort() {
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        Club secondary = Club.createExisting(UUID.randomUUID(), "C.T.T. Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary, secondary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(clubs, new InMemoryFederatedClubs(List.of()), new RecordingEventBus());

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), secondary.getId()), "A", primary.getId(), null, null));

        assertFalse(response.isSuccess());
    }

    @Test
    void publishesTheConsolidationEventWithPreMergeSourceNamesAndTheActingUser() {
        RecordingEventBus eventBus = new RecordingEventBus();
        UUID actingUserId = UUID.randomUUID();
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        Club secondary = Club.createExisting(UUID.randomUUID(), "C.T.T. Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary, secondary));
        InMemoryFederatedClubs federatedClubs = new InMemoryFederatedClubs(List.of());
        ConsolidateClubsCommandHandler handler =
                new ConsolidateClubsCommandHandler(clubs, federatedClubs, eventBus);

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), secondary.getId()), "CTT Terrassa Consolidat", primary.getId(),
                actingUserId, "admin"));

        assertTrue(response.isSuccess());
        List<ClubsConsolidatedEvent> consolidated = eventBus.published.stream()
                .filter(ClubsConsolidatedEvent.class::isInstance)
                .map(ClubsConsolidatedEvent.class::cast)
                .toList();
        assertEquals(1, consolidated.size());

        ClubsConsolidatedEvent event = consolidated.get(0);
        assertEquals(primary.getId(), event.getPrimaryClubId());
        assertEquals("CTT Terrassa Consolidat", event.getCanonicalName());
        assertEquals(actingUserId, event.getPerformedByUserId());
        assertEquals("admin", event.getPerformedByUsername());

        // Pre-merge name, captured before the club row was deleted.
        assertEquals(
                List.of(ConsolidationActionClub.of(secondary.getId(), "C.T.T. Terrassa")),
                event.getMergedClubs());
        assertEquals(List.of(secondary.getId()), event.getMergedClubIds());
    }

    @Test
    void recordsNoActingUserWhenTheCommandCarriesNone() {
        RecordingEventBus eventBus = new RecordingEventBus();
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        Club secondary = Club.createExisting(UUID.randomUUID(), "C.T.T. Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary, secondary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(
                clubs, new InMemoryFederatedClubs(List.of()), eventBus);

        handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), secondary.getId()), "CTT Terrassa Consolidat", primary.getId(),
                null, null));

        ClubsConsolidatedEvent event = eventBus.published.stream()
                .filter(ClubsConsolidatedEvent.class::isInstance)
                .map(ClubsConsolidatedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertNull(event.getPerformedByUserId());
        assertNull(event.getPerformedByUsername());
    }

    @Test
    void publishesNothingWhenTheConsolidationIsRejected() {
        RecordingEventBus eventBus = new RecordingEventBus();
        Club primary = Club.createExisting(UUID.randomUUID(), "CTT Terrassa");
        InMemoryClubs clubs = new InMemoryClubs(List.of(primary));
        ConsolidateClubsCommandHandler handler = new ConsolidateClubsCommandHandler(
                clubs, new InMemoryFederatedClubs(List.of()), eventBus);

        DomainCommandResponse response = handler.handle(new ConsolidateClubsCommand(
                ZonedDateTime.now(), UUID.randomUUID().toString(),
                List.of(primary.getId(), UUID.randomUUID()), "CTT Terrassa Consolidat", primary.getId(),
                null, null));

        assertFalse(response.isSuccess());
        assertTrue(eventBus.published.isEmpty());
    }

    private static final class RecordingEventBus implements EventBus {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }

        @Override
        public void registerSubscriber(DomainEventSubscriber subscriber) {
            // Not used by these tests.
        }
    }

    private static final class InMemoryClubs implements ClubRepository {
        private final List<Club> clubs;

        private InMemoryClubs(List<Club> clubs) {
            this.clubs = new ArrayList<>(clubs);
        }

        @Override
        public Optional<Club> findClubById(UUID id) {
            return clubs.stream().filter(club -> club.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<Club> findClubByExactName(String name) {
            return clubs.stream().filter(club -> club.getName().equals(name)).findFirst();
        }

        @Override
        public List<Club> findAllClubs() {
            return List.copyOf(clubs);
        }

        @Override
        public void saveClub(Club club) {
            clubs.removeIf(existing -> existing.getId().equals(club.getId()));
            clubs.add(club);
        }

        @Override
        public void deleteClubById(UUID id) {
            clubs.removeIf(club -> club.getId().equals(id));
        }
    }

    private static final class InMemoryFederatedClubs implements FederatedClubRepository {
        private final List<FederatedClub> federatedClubs;

        private InMemoryFederatedClubs(List<FederatedClub> federatedClubs) {
            this.federatedClubs = new ArrayList<>(federatedClubs);
        }

        @Override
        public Optional<FederatedClub> findFederatedClubById(UUID id) {
            return federatedClubs.stream().filter(club -> club.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<FederatedClub> findFederatedClubBySourceAndName(ImportSource source, String name) {
            return federatedClubs.stream()
                    .filter(club -> club.getSource() == source && club.getName().equals(name))
                    .findFirst();
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsByClubId(UUID clubId) {
            return federatedClubs.stream()
                    .filter(club -> club.getClub().map(Club::getId).map(clubId::equals).orElse(false))
                    .toList();
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsByFragmentsInName(List<String> fragments) {
            return List.of();
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsBySourceAndFragmentsInName(
                ImportSource source, List<String> fragments) {
            return List.of();
        }

        @Override
        public void saveFederatedClub(FederatedClub club) {
            federatedClubs.removeIf(existing -> existing.getId().equals(club.getId()));
            federatedClubs.add(club);
        }

        @Override
        public void deleteFederatedClubById(UUID id) {
            federatedClubs.removeIf(club -> club.getId().equals(id));
        }
    }
}
