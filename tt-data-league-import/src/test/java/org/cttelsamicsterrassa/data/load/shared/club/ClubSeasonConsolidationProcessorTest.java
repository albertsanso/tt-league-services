package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubSeasonConsolidationProcessorTest {

    private InMemoryRepositories.Clubs clubs;
    private InMemoryRepositories.ClubSeasons clubSeasons;
    private ClubSeasonConsolidationProcessor processor;

    @BeforeEach
    void setUp() {
        clubs = new InMemoryRepositories.Clubs();
        clubSeasons = new InMemoryRepositories.ClubSeasons();
        processor = new ClubSeasonConsolidationProcessor(clubs, clubSeasons);
    }

    @Test
    void groupsNormalizedSpellingsAndVerifiedAbbreviations() {
        ClubSeason first = saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        ClubSeason second = saveSeason(ImportSource.FCTT, "hortitec   alzira", Season.of(2024), null);

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.FCTT);

        assertEquals(1, summary.exactGroups());
        assertEquals(1, summary.clubsCreated());
        assertEquals(2, summary.registrationsReassociated());
        assertEquals(sameClub(first.getId()), sameClub(second.getId()));
    }

    @Test
    void reassociatesAPermittedTypoAndWarnsOnRejectedFuzzyCases() {
        ClubSeason typoLeft = saveSeason(ImportSource.BCNESA, "FALCONS DE SABADELL", Season.of(2020), null);
        ClubSeason typoRight = saveSeason(ImportSource.BCNESA, "FALCONS DE SABDELL", Season.of(2021), null);
        saveSeason(ImportSource.BCNESA, "GIRONA", Season.of(2020), null);
        saveSeason(ImportSource.BCNESA, "REUS", Season.of(2021), null);
        saveSeason(ImportSource.BCNESA, "CTT ATENEU", Season.of(2020), null);
        saveSeason(ImportSource.BCNESA, "CTT DELS HORTS", Season.of(2020), null);
        saveSeason(ImportSource.BCNESA, "FALCONS DE TERRASSA", Season.of(2022), null);

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.BCNESA);

        assertEquals(1, summary.acceptedFuzzyGroups());
        assertEquals(sameClub(typoLeft.getId()), sameClub(typoRight.getId()));
        assertTrue(summary.warnings().stream().anyMatch(warning -> warning.reason().contains("short")));
        assertTrue(summary.warnings().stream().anyMatch(warning -> warning.reason().contains("tokens")));
        assertTrue(summary.warnings().stream().anyMatch(warning -> warning.reason().contains("threshold")));
    }

    @Test
    void neverGroupsTheSameNameAcrossSources() {
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        saveSeason(ImportSource.BCNESA, "HORTITEC ALZIRA TT", Season.of(2023), null);

        processor.consolidate(ImportSource.FCTT);
        processor.consolidate(ImportSource.BCNESA);

        Club fctt = clubSeasons.findAllClubSeasonsBySource(ImportSource.FCTT).getFirst().getClub().orElseThrow();
        Club bcnesa = clubSeasons.findAllClubSeasonsBySource(ImportSource.BCNESA).getFirst().getClub().orElseThrow();
        assertEquals(ImportSource.FCTT, fctt.getSource());
        assertEquals(ImportSource.BCNESA, bcnesa.getSource());
        assertTrue(!fctt.getId().equals(bcnesa.getId()));
    }

    @Test
    void retainsSeasonSpecificIdentityWhileSharingOneClub() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        clubSeasons.saveClubSeason(ClubSeason.createExisting(
                firstId, ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null));
        clubSeasons.saveClubSeason(ClubSeason.createExisting(
                secondId, ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2024), null));

        processor.consolidate(ImportSource.FCTT);

        ClubSeason first = clubSeasons.findClubSeasonById(firstId).orElseThrow();
        ClubSeason second = clubSeasons.findClubSeasonById(secondId).orElseThrow();
        assertEquals(firstId, first.getId());
        assertEquals(secondId, second.getId());
        assertEquals("HORTITEC ALZIRA TT", first.getName());
        assertEquals(Season.of(2023), first.getSeason());
        assertEquals(Season.of(2024), second.getSeason());
        assertEquals(first.getClub().orElseThrow().getId(), second.getClub().orElseThrow().getId());
    }

    @Test
    void reusesAnAgreedClubCreatesOneWhenMissingAndSkipsConflicts() {
        Club agreed = Club.createNew(ImportSource.FCTT, "HORTITEC ALZIRA TT");
        clubs.saveClub(agreed);
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), agreed);
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2024), agreed);

        ClubConsolidationSummary agreedSummary = processor.consolidate(ImportSource.FCTT);
        assertEquals(0, agreedSummary.clubsCreated());
        assertEquals(2, agreedSummary.alreadyCorrectRegistrations());

        saveSeason(ImportSource.FCTT, "CTT GIRONA", Season.of(2023), null);
        saveSeason(ImportSource.FCTT, "CTT GIRONA", Season.of(2024), null);
        ClubConsolidationSummary created = processor.consolidate(ImportSource.FCTT);
        assertEquals(1, created.clubsCreated());

        Club left = Club.createNew(ImportSource.FCTT, "CLUB NORD");
        Club right = Club.createNew(ImportSource.FCTT, "CLUB NORD B");
        clubs.saveClub(left);
        clubs.saveClub(right);
        saveSeason(ImportSource.FCTT, "CLUB NORD", Season.of(2023), left);
        saveSeason(ImportSource.FCTT, "club nord", Season.of(2024), right);
        int clubsBeforeConflict = clubs.size();
        ClubConsolidationSummary conflict = processor.consolidate(ImportSource.FCTT);
        assertTrue(conflict.warnings().stream().anyMatch(warning -> warning.reason().contains("Conflicting")));
        assertEquals(clubsBeforeConflict, clubs.size());
        assertEquals(left.getId(), clubSeasons.findAllClubSeasonsBySource(ImportSource.FCTT).stream()
                .filter(cs -> "CLUB NORD".equals(cs.getName()))
                .findFirst().orElseThrow().getClub().orElseThrow().getId());
    }

    @Test
    void secondRunIsIdempotent() {
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA", Season.of(2024), null);

        ClubConsolidationSummary first = processor.consolidate(ImportSource.FCTT);
        ClubConsolidationSummary second = processor.consolidate(ImportSource.FCTT);

        assertEquals(1, first.clubsCreated());
        assertEquals(2, first.registrationsReassociated());
        assertEquals(0, second.clubsCreated());
        assertEquals(0, second.registrationsReassociated());
        assertEquals(2, second.alreadyCorrectRegistrations());
    }

    @Test
    void disablesAutomaticRfetmConsolidation() {
        saveSeason(ImportSource.RFETM, "HORTITEC ALZIRA TT", Season.of(2023), null);
        ClubConsolidationSummary summary = processor.consolidate(ImportSource.RFETM);
        assertEquals(0, summary.clubsCreated());
        assertEquals(0, summary.registrationsReassociated());
        assertEquals(1, summary.errors().size());
        assertTrue(clubSeasons.findAllClubSeasonsBySource(ImportSource.RFETM).getFirst().getClub().isEmpty());
    }

    @Test
    void dryRunReportsTheSameCountsWithoutSaving() {
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA", Season.of(2024), null);

        ClubConsolidationSummary report = processor.consolidate(ImportSource.FCTT, ConsolidationMode.REPORT);

        assertEquals(1, report.clubsCreated());
        assertEquals(2, report.registrationsReassociated());
        assertEquals(0, clubs.size());
        assertTrue(clubSeasons.findAllClubSeasonsBySource(ImportSource.FCTT).stream()
                .allMatch(cs -> cs.getClub().isEmpty()));
    }

    @Test
    void canonicalizesThePromptExamplesWithOneClubNamePerGroup() {
        assertCanonicalName("CC SANT ANDREU",
                "CC SANT ANDREU", "CC SANT ANDREU A", "CC SANT ANDREU B", "CC SANT ANDREU A Vet");
        assertCanonicalName("CETT SANT ANDREU DE LA BARCA",
                "CETT SANT ANDREU DE LA BARCA", "CETT SANT ANDREU DE LA BARCA A",
                "CETT SANT ANDREU DE LA BARCA B", "CETT ST ANDREU DE LA BARCA");
        assertCanonicalName("CTT COLLBATO",
                "CTT COLLBATO", "CTT COLLBATO LA CASSOLA", "CTT COLLBATO A", "CTT COLLBATO B");
        assertCanonicalName("CTT DELS HORTS 2000",
                "CTT DELS HORTS 2000", "CTT DELS HORTS 2000 A", "CTT DELS HORTS 2000 B");
        assertCanonicalName("CTT ELS AMICS DE TERRASSA",
                "CTT ELS AMICS DE TERRASSA", "CTT ELS AMICS TERRASSA A",
                "CTT ELS AMICS DE TERRASSA A", "CTT ELS AMICS TERRASSA B",
                "CTT AMICS TERRASSA B", "CTT ELS AMICS TERRASSA C");
        assertCanonicalName("TT JOVES CTDFELS",
                "ÀNECBLAU - TT JOVES CTDFELS", "ÀNECBLAU - TT ELS JOVES");
        assertCanonicalName("CTT LA BISBAL DEL PENEDÈS",
                "CTT LA BISBAL DEL PENEDÈS A", "CTT LA BISBAL DEL PENEDÈS B");
        assertCanonicalName("CTT COLÒNIA GÜELL",
                "CTT COLÒNIA GÜELL A", "CTT COLÒNIA GÜELL B");
        assertCanonicalName("CTT SANT QUIRZE DEL VALLÈS",
                "CTT SANT QUIRZE DEL VALLÈS - Sen A",
                "CTT SANT QUIRZE DEL VALLÈS - Sen B",
                "CTT SANT QUIRZE DEL VALLÈS - Vet A",
                "CTT SANT QUIRZE DEL VALLÈS - Vet C",
                "CTT SANT QUIRZE DEL VALLÈS - Vet B");
        assertCanonicalName("TT SANT ANDREU",
                "TT SANT ANDREU -A-", "TT SANT ANDREU -B-");
        assertCanonicalName("CTT SANT QUIRZE DEL VALLÈS",
                "CTT SANT QUIRZE DEL VALLÈS - Sen A",
                "CTT ST QUIRZE DEL VALLÈS - Sen C",
                "CTT SANT QUIRZE DEL VALLÈS - Vet A",
                "CTT SANT QUIRZE DEL VALLÈS - Sen B",
                "CTT ST QUIRZE DEL VALLÈS - Vet D A",
                "CTT ST QUIRZE DEL VALLÈS - Vet C",
                "CTT ST QUIRZE DEL VALLÈS - Vet B",
                "CTT ST QUIRZE DEL VALLÈS - Vet A",
                "CTT ST QUIRZE DEL VALLÈS - Sen B",
                "CTT ST QUIRZE DEL VALLÈS - Vet E B",
                "CTT SANT QUIRZE DEL VALLÈS - Vet B",
                "CTT SANT QUIRZE DEL VALLÈS - Vet C",
                "CTT ST QUIRZE DEL VALLÈS - Sen D");
        assertCanonicalName("MANUFACTURAS DEPORTIVAS",
                "MANUFACTURAS DEPORTIVAS", "MANUFACTURAS DEPORTIVAS",
                "MANUFACTURAS DEPORTIVAS 'A'", "MANUFACTURAS DEPORTIVAS 'B'");
        assertCanonicalName("TENNIS TAULA CASSA",
                "TENNIS TAULA CASSA", "TENNIS TAULA CASSA",
                "TENNIS TAULA CASSÀ");
        assertCanonicalName("OBERENA",
                "OBERENA 'A'", "OBERENA \"A\"");
        assertCanonicalName("CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'");
    }

    @Test
    void renamesAnAgreedCanonicalClubToTheBetterDisplayName() {
        Club club = Club.createNew(ImportSource.BCNESA, "CETT ST ANDREU DE LA BARCA");
        clubs.saveClub(club);
        saveSeason(ImportSource.BCNESA, "CETT ST ANDREU DE LA BARCA", Season.of(2023), club);
        saveSeason(ImportSource.BCNESA, "CETT SANT ANDREU DE LA BARCA", Season.of(2024), club);

        processor.consolidate(ImportSource.BCNESA);

        assertEquals("CETT SANT ANDREU DE LA BARCA", clubs.findClubById(club.getId()).orElseThrow().getName());
    }

    private void assertCanonicalName(String expected, String... names) {
        setUp();
        for (int i = 0; i < names.length; i++) {
            saveSeason(ImportSource.BCNESA, names[i], Season.of(2020 + i), null);
        }

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.BCNESA);

        assertEquals(1, summary.consolidations().size());
        ConsolidatedClub consolidation = summary.consolidations().getFirst();
        assertEquals(expected, consolidation.canonicalDisplayName());
        assertEquals(names.length, consolidation.registrationIds().size());
    }

    private ClubSeason saveSeason(ImportSource source, String name, Season season, Club club) {
        ClubSeason created = ClubSeason.createNew(source, name, season, club);
        clubSeasons.saveClubSeason(created);
        return created;
    }

    private UUID sameClub(UUID clubSeasonId) {
        return clubSeasons.findClubSeasonById(clubSeasonId).orElseThrow().getClub().orElseThrow().getId();
    }
}
