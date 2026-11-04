package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationAction;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClub;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionType;
import org.cttelsamicsterrassa.data.core.domain.consolidation.repository.ConsolidationActionRepository;
import org.cttelsamicsterrassa.data.core.repository.jpa.JpaTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(JpaTestSupportConfiguration.class)
@Transactional
class ConsolidationActionRepositoryJpaTest {

    @Autowired
    private ConsolidationActionRepository consolidationActionRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Test
    void storesAndReadsBackAConsolidationAction() {
        UUID sourceClubId = UUID.randomUUID();
        UUID targetClubId = UUID.randomUUID();
        UUID actingUserId = UUID.randomUUID();
        ZonedDateTime occurredOn = ZonedDateTime.now();

        ConsolidationAction action = ConsolidationAction.createNew(
                ConsolidationActionType.MERGE,
                occurredOn,
                actingUserId,
                "admin",
                "CTT Terrassa Consolidat",
                List.of(ConsolidationActionClub.of(sourceClubId, "C.T.T. Terrassa")),
                List.of(ConsolidationActionClub.of(targetClubId, "CTT Terrassa Consolidat")));
        consolidationActionRepository.save(action);

        ConsolidationAction stored = consolidationActionRepository.findById(action.getId()).orElseThrow();
        assertEquals(ConsolidationActionType.MERGE, stored.getType());
        assertEquals(actingUserId, stored.getPerformedByUserId());
        assertEquals("admin", stored.getPerformedByUsername());
        assertEquals("CTT Terrassa Consolidat", stored.getCanonicalName());
        assertEquals(
                List.of(ConsolidationActionClub.of(sourceClubId, "C.T.T. Terrassa")),
                stored.getSourceClubs());
        assertEquals(
                List.of(ConsolidationActionClub.of(targetClubId, "CTT Terrassa Consolidat")),
                stored.getTargetClubs());
    }

    /**
     * The regression this table's FK-free {@code club_id} column exists for: the merged-away club
     * rows are deleted by the same operation that writes the audit record.
     */
    @Test
    void keepsTheRecordWhenTheRecordedClubRowsNoLongerExist() {
        Club merged = Club.createNew("C.T.T. Terrassa");
        Club surviving = Club.createNew("CTT Terrassa");
        clubRepository.saveClub(merged);
        clubRepository.saveClub(surviving);

        ConsolidationAction action = ConsolidationAction.createNew(
                ConsolidationActionType.MERGE,
                ZonedDateTime.now(),
                null,
                null,
                "CTT Terrassa Consolidat",
                List.of(ConsolidationActionClub.of(merged.getId(), merged.getName())),
                List.of(ConsolidationActionClub.of(surviving.getId(), "CTT Terrassa Consolidat")));
        consolidationActionRepository.save(action);

        clubRepository.deleteClubById(merged.getId());

        ConsolidationAction stored = consolidationActionRepository.findById(action.getId()).orElseThrow();
        assertEquals(merged.getId(), stored.getSourceClubs().get(0).getClubId());
        assertEquals("C.T.T. Terrassa", stored.getSourceClubs().get(0).getClubName());
        assertTrue(clubRepository.findClubById(merged.getId()).isEmpty());
    }

    @Test
    void findsHistoryByClubIdOnTheSourceSide() {
        UUID sourceClubId = UUID.randomUUID();
        ConsolidationAction action = saveAction(sourceClubId, UUID.randomUUID());

        List<ConsolidationAction> found = consolidationActionRepository.findAllByClubId(sourceClubId);

        assertEquals(1, found.size());
        assertEquals(action.getId(), found.get(0).getId());
    }

    @Test
    void findsHistoryByClubIdOnTheTargetSide() {
        UUID targetClubId = UUID.randomUUID();
        ConsolidationAction action = saveAction(UUID.randomUUID(), targetClubId);

        List<ConsolidationAction> found = consolidationActionRepository.findAllByClubId(targetClubId);

        assertEquals(1, found.size());
        assertEquals(action.getId(), found.get(0).getId());
    }

    @Test
    void returnsNoHistoryForAnUnrelatedClubId() {
        saveAction(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(consolidationActionRepository.findAllByClubId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void ordersTheFullLogMostRecentFirst() {
        ZonedDateTime now = ZonedDateTime.now();
        ConsolidationAction older = saveActionAt(now.minusDays(2));
        ConsolidationAction newer = saveActionAt(now.minusDays(1));

        List<UUID> ids = consolidationActionRepository.findAllOrderByOccurredOnDesc().stream()
                .map(ConsolidationAction::getId)
                .filter(id -> id.equals(older.getId()) || id.equals(newer.getId()))
                .toList();

        assertEquals(List.of(newer.getId(), older.getId()), ids);
    }

    @Test
    void storesAnActionWithoutAnActingUser() {
        ConsolidationAction action = saveAction(UUID.randomUUID(), UUID.randomUUID());

        ConsolidationAction stored = consolidationActionRepository.findById(action.getId()).orElseThrow();
        assertNull(stored.getPerformedByUserId());
        assertNull(stored.getPerformedByUsername());
    }

    private ConsolidationAction saveAction(UUID sourceClubId, UUID targetClubId) {
        ConsolidationAction action = ConsolidationAction.createNew(
                ConsolidationActionType.MERGE,
                ZonedDateTime.now(),
                null,
                null,
                "CTT Terrassa Consolidat",
                List.of(ConsolidationActionClub.of(sourceClubId, "C.T.T. Terrassa")),
                List.of(ConsolidationActionClub.of(targetClubId, "CTT Terrassa Consolidat")));
        consolidationActionRepository.save(action);
        return action;
    }

    private ConsolidationAction saveActionAt(ZonedDateTime occurredOn) {
        ConsolidationAction action = ConsolidationAction.createNew(
                ConsolidationActionType.MERGE,
                occurredOn,
                null,
                null,
                "CTT Terrassa Consolidat",
                List.of(ConsolidationActionClub.of(UUID.randomUUID(), "C.T.T. Terrassa")),
                List.of(ConsolidationActionClub.of(UUID.randomUUID(), "CTT Terrassa Consolidat")));
        consolidationActionRepository.save(action);
        return action;
    }
}
