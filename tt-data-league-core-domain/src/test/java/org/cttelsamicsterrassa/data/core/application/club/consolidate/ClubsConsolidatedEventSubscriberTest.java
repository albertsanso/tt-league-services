package org.cttelsamicsterrassa.data.core.application.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.event.ClubsConsolidatedEvent;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionType;
import org.cttelsamicsterrassa.data.core.domain.consolidation.repository.ConsolidationActionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClubsConsolidatedEventSubscriberTest {

    @Test
    void storesTheConsolidationAsAHistoricAction() {
        UUID primaryClubId = UUID.randomUUID();
        UUID mergedClubId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        ClubsConsolidatedEvent event = ClubsConsolidatedEvent.of(
                primaryClubId,
                "CTT Terrassa Consolidat",
                "CTT Terrassa Consolidat",
                List.of(ConsolidationActionClub.of(mergedClubId, "C.T.T. Terrassa")),
                actingUserId,
                "admin");
        InMemoryConsolidationActions actions = new InMemoryConsolidationActions();

        new ClubsConsolidatedEventSubscriber(actions).handle(event);

        assertEquals(1, actions.saved.size());
        ConsolidationAction action = actions.saved.get(0);
        assertEquals(ConsolidationActionType.MERGE, action.getType());
        assertEquals(event.getOccurredOn(), action.getOccurredOn());
        assertEquals(actingUserId, action.getPerformedByUserId());
        assertEquals("admin", action.getPerformedByUsername());
        assertEquals("CTT Terrassa Consolidat", action.getCanonicalName());
        assertEquals(
                List.of(ConsolidationActionClub.of(mergedClubId, "C.T.T. Terrassa")),
                action.getSourceClubs());
        assertEquals(
                List.of(ConsolidationActionClub.of(primaryClubId, "CTT Terrassa Consolidat")),
                action.getTargetClubs());
    }

    @Test
    void storesTheActionWithoutAUserWhenTheEventCarriesNone() {
        ClubsConsolidatedEvent event = ClubsConsolidatedEvent.of(
                UUID.randomUUID(),
                "CTT Terrassa",
                "CTT Terrassa",
                List.of(ConsolidationActionClub.of(UUID.randomUUID(), "C.T.T. Terrassa")),
                null,
                null);
        InMemoryConsolidationActions actions = new InMemoryConsolidationActions();

        new ClubsConsolidatedEventSubscriber(actions).handle(event);

        assertEquals(1, actions.saved.size());
        assertNull(actions.saved.get(0).getPerformedByUserId());
        assertNull(actions.saved.get(0).getPerformedByUsername());
    }

    @Test
    void swallowsRepositoryFailuresSoAPersistedMergeIsNeverReportedAsFailed() {
        ClubsConsolidatedEvent event = ClubsConsolidatedEvent.of(
                UUID.randomUUID(),
                "CTT Terrassa",
                "CTT Terrassa",
                List.of(ConsolidationActionClub.of(UUID.randomUUID(), "C.T.T. Terrassa")),
                null,
                null);
        ConsolidationActionRepository failing = new InMemoryConsolidationActions() {
            @Override
            public void save(ConsolidationAction action) {
                throw new IllegalStateException("database unavailable");
            }
        };

        assertDoesNotThrow(() -> new ClubsConsolidatedEventSubscriber(failing).handle(event));
    }

    @Test
    void handlesTheConsolidationEventType() {
        assertEquals(
                ClubsConsolidatedEvent.class,
                new ClubsConsolidatedEventSubscriber(new InMemoryConsolidationActions()).handles());
    }

    private static class InMemoryConsolidationActions implements ConsolidationActionRepository {
        private final List<ConsolidationAction> saved = new ArrayList<>();

        @Override
        public void save(ConsolidationAction action) {
            saved.add(action);
        }

        @Override
        public Optional<ConsolidationAction> findById(UUID id) {
            return saved.stream().filter(action -> action.getId().equals(id)).findFirst();
        }

        @Override
        public List<ConsolidationAction> findAllOrderByOccurredOnDesc() {
            return List.copyOf(saved);
        }

        @Override
        public List<ConsolidationAction> findAllByClubId(UUID clubId) {
            return saved.stream()
                    .filter(action -> action.getSourceClubs().stream()
                            .anyMatch(club -> club.getClubId().equals(clubId))
                            || action.getTargetClubs().stream()
                            .anyMatch(club -> club.getClubId().equals(clubId)))
                    .toList();
        }
    }
}
