package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.TeamToClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidatedClub;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamToClubConsolidationProcessorTest {

    private InMemoryRepositories.Clubs clubs;
    private InMemoryRepositories.Teams teams;
    private TeamToClubConsolidationProcessor processor;

    @BeforeEach
    void setUp() {
        clubs = new InMemoryRepositories.Clubs();
        teams = new InMemoryRepositories.Teams();
        processor = new TeamToClubConsolidationProcessor(clubs, teams);
    }

    @Test
    void groupsNormalizedSpellingsAndVerifiedAbbreviations() {
        Team first = saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        Team second = saveSeason(ImportSource.FCTT, "hortitec   alzira", Season.of(2024), null);

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.FCTT);

        assertEquals(1, summary.exactGroups());
        assertEquals(1, summary.clubsCreated());
        assertEquals(2, summary.registrationsReassociated());
        assertEquals(sameClub(first.getId()), sameClub(second.getId()));
    }

    @Test
    void reassociatesAPermittedTypoAndWarnsOnRejectedFuzzyCases() {
        Team typoLeft = saveSeason(ImportSource.BCNESA, "FALCONS DE SABADELL", Season.of(2020), null);
        Team typoRight = saveSeason(ImportSource.BCNESA, "FALCONS DE SABDELL", Season.of(2021), null);
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

        Club fctt = teams.findAllTeamsBySource(ImportSource.FCTT).getFirst().getClub().orElseThrow();
        Club bcnesa = teams.findAllTeamsBySource(ImportSource.BCNESA).getFirst().getClub().orElseThrow();
        assertEquals(ImportSource.FCTT, fctt.getSource());
        assertEquals(ImportSource.BCNESA, bcnesa.getSource());
        assertTrue(!fctt.getId().equals(bcnesa.getId()));
    }

    @Test
    void retainsSeasonSpecificIdentityWhileSharingOneClub() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        teams.saveTeam(Team.createExisting(
                firstId, ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null));
        teams.saveTeam(Team.createExisting(
                secondId, ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2024), null));

        processor.consolidate(ImportSource.FCTT);

        Team first = teams.findTeamById(firstId).orElseThrow();
        Team second = teams.findTeamById(secondId).orElseThrow();
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
        assertEquals(left.getId(), teams.findAllTeamsBySource(ImportSource.FCTT).stream()
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
        assertTrue(teams.findAllTeamsBySource(ImportSource.RFETM).getFirst().getClub().isEmpty());
    }

    @Test
    void dryRunReportsTheSameCountsWithoutSaving() {
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA TT", Season.of(2023), null);
        saveSeason(ImportSource.FCTT, "HORTITEC ALZIRA", Season.of(2024), null);

        ClubConsolidationSummary report = processor.consolidate(ImportSource.FCTT, ConsolidationMode.REPORT);

        assertEquals(1, report.clubsCreated());
        assertEquals(2, report.registrationsReassociated());
        assertEquals(0, clubs.size());
        assertTrue(teams.findAllTeamsBySource(ImportSource.FCTT).stream()
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
    void keepsDistinctClubsWithTheSameNamePrefixSeparate() {
        String[] names = {
                "CLUB TENNIS TAULA BARCELONA",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
                "CLUB TENNIS TAULA TORELLÓ",
                "CLUB TENNIS TAULA LA BISBAL",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
                "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'",
                "CLUB TENNIS TAULA ALTEA",
                "CLUB TENNIS TAULA SANTISIMO SALVADOR",
                "CLUB TENNIS TAULA OLESA"
        };

        saveNames(ImportSource.BCNESA, names);

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.BCNESA);

        assertEquals(7, summary.consolidations().size());
        assertEquals(7, clubs.size());
        assertEquals(7, teams.findAllTeamsBySource(ImportSource.BCNESA).stream()
                .map(team -> team.getClub().orElseThrow().getId())
                .distinct()
                .count());
        assertEquals(
                1,
                teams.findAllTeamsBySource(ImportSource.BCNESA).stream()
                        .filter(team -> team.getName().contains("TRAMUNTANA FIGUERES"))
                        .map(team -> team.getClub().orElseThrow().getId())
                        .distinct()
                        .count());
    }

    @Test
    void keepsDistinctSpanishClubsWithTheSameNamePrefixSeparate() {
        String[] names = {
                "CLUB TENIS DE MESA SALUD Y DEPORTE",
                "CLUB TENIS DE MESA TABOR AÑAVINGO",
                "CLUB TENIS DE MESA COSLADA",
                "CLUB TENIS DE MESA VILLA DE VALDEMORO",
                "CLUB TENIS DE MESA MOS Dismac",
                "CLUB TENIS DE MESA MAZDA JEREZ",
                "CLUB TENIS DE MESA TECNIK '87",
                "CLUB TENIS DE MESA VIGO",
                "CLUB TENIS DE MESA VICAR",
                "CLUB TENIS DE MESA ALCAZAR",
                "CLUB TENIS DE MESA BASAURI"
        };

        saveNames(ImportSource.BCNESA, names);

        ClubConsolidationSummary summary = processor.consolidate(ImportSource.BCNESA);

        assertEquals(names.length, summary.consolidations().size());
        assertEquals(names.length, clubs.size());
        assertEquals(names.length, teams.findAllTeamsBySource(ImportSource.BCNESA).stream()
                .map(team -> team.getClub().orElseThrow().getId())
                .distinct()
                .count());
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

    private Team saveSeason(ImportSource source, String name, Season season, Club club) {
        Team created = Team.createNew(source, name, season, club);
        teams.saveTeam(created);
        return created;
    }

    private void saveNames(ImportSource source, String... names) {
        for (int i = 0; i < names.length; i++) {
            saveSeason(source, names[i], Season.of(2020 + i), null);
        }
    }

    private UUID sameClub(UUID teamId) {
        return teams.findTeamById(teamId).orElseThrow().getClub().orElseThrow().getId();
    }
}
