package org.cttelsamicsterrassa.data.load.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubToCanonicalClubConsolidationProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FederatedClubToCanonicalClubConsolidationProcessorTest {

    @Test
    void groupsSourceNamesAndReusesCanonicalClubIdempotently() {
        InMemoryRepositories.Clubs federatedClubs = new InMemoryRepositories.Clubs();
        InMemoryRepositories.CanonicalClubs canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        FederatedClub first = FederatedClub.createNew(ImportSource.FCTT, "Club Alzira");
        FederatedClub second = FederatedClub.createNew(ImportSource.FCTT, " CLUB ALZIRA ");
        federatedClubs.saveFederatedClub(first);
        federatedClubs.saveFederatedClub(second);

        FederatedClubToCanonicalClubConsolidationProcessor processor =
                new FederatedClubToCanonicalClubConsolidationProcessor(federatedClubs, canonicalClubs);

        var summary = processor.consolidate(ImportSource.FCTT);

        assertEquals(1, summary.exactGroups());
        assertEquals(2, summary.canonicalLinksCreated());
        assertEquals(1, summary.clubsCreated());
        assertEquals(1, canonicalClubs.size());
        Club canonical = federatedClubs.findFederatedClubById(first.getId()).orElseThrow().getClub().orElseThrow();
        assertSame(canonical, federatedClubs.findFederatedClubById(second.getId()).orElseThrow().getClub().orElseThrow());

        var secondRun = processor.consolidate(ImportSource.FCTT);
        assertEquals(0, secondRun.clubsCreated());
        assertEquals(0, secondRun.canonicalLinksCreated());
        assertEquals(2, secondRun.alreadyCorrectRegistrations());
    }

    @Test
    void reportModeDoesNotPersistLinksOrCanonicalClubs() {
        InMemoryRepositories.Clubs federatedClubs = new InMemoryRepositories.Clubs();
        InMemoryRepositories.CanonicalClubs canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        federatedClubs.saveFederatedClub(FederatedClub.createNew(ImportSource.BCNESA, "Club Nova"));

        FederatedClubToCanonicalClubConsolidationProcessor processor =
                new FederatedClubToCanonicalClubConsolidationProcessor(federatedClubs, canonicalClubs);

        var summary = processor.consolidate(ImportSource.BCNESA, ConsolidationMode.REPORT);

        assertEquals(1, summary.canonicalLinksCreated());
        assertEquals(1, summary.clubsCreated());
        assertEquals(0, canonicalClubs.size());
        assertEquals(false, federatedClubs.findFederatedClubBySourceAndName(
                ImportSource.BCNESA, "Club Nova").orElseThrow().getClub().isPresent());
    }

    @Test
    void clustersSimilarNamesAndUsesTheirCommonRepresentativeTerms() {
        InMemoryRepositories.Clubs federatedClubs = new InMemoryRepositories.Clubs();
        InMemoryRepositories.CanonicalClubs canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        federatedClubs.saveFederatedClub(FederatedClub.createNew(
                ImportSource.RFETM, "CLUB TENNIS TAULA ELS AMICS TERRASSA"));
        federatedClubs.saveFederatedClub(FederatedClub.createNew(
                ImportSource.RFETM, "CTT ELS AMICS DE TERRASSA"));
        federatedClubs.saveFederatedClub(FederatedClub.createNew(
                ImportSource.RFETM, "CTT ELS AMICS TERRASSA"));

        var summary = new FederatedClubToCanonicalClubConsolidationProcessor(
                federatedClubs, canonicalClubs).consolidate(ImportSource.RFETM);

        assertEquals(1, summary.acceptedFuzzyGroups());
        assertEquals("AMICS TERRASSA", summary.consolidations().getFirst().canonicalDisplayName());
        assertEquals(1, canonicalClubs.size());
        assertEquals(3, summary.canonicalLinksCreated());
    }

    @Test
    void reusesASimilarExistingCanonicalClubForTheCluster() {
        InMemoryRepositories.Clubs federatedClubs = new InMemoryRepositories.Clubs();
        InMemoryRepositories.CanonicalClubs canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        Club existing = Club.createNew("AMICS DE TERRASSA");
        canonicalClubs.saveClub(existing);
        FederatedClub first = FederatedClub.createNew(ImportSource.RFETM, "CTT ELS AMICS TERRASSA");
        FederatedClub second = FederatedClub.createNew(ImportSource.RFETM, "CLUB TENNIS TAULA ELS AMICS TERRASSA");
        federatedClubs.saveFederatedClub(first);
        federatedClubs.saveFederatedClub(second);

        var summary = new FederatedClubToCanonicalClubConsolidationProcessor(
                federatedClubs, canonicalClubs).consolidate(ImportSource.RFETM);

        assertEquals("AMICS TERRASSA", summary.consolidations().getFirst().canonicalDisplayName());
        assertEquals(0, summary.clubsCreated());
        assertEquals(existing.getId(), federatedClubs.findFederatedClubById(first.getId())
                .orElseThrow().getClub().orElseThrow().getId());
        assertEquals(existing.getId(), federatedClubs.findFederatedClubById(second.getId())
                .orElseThrow().getClub().orElseThrow().getId());
    }
}
