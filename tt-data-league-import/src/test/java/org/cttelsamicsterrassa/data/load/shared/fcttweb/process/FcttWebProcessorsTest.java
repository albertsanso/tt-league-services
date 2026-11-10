package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.fctt.process.FcttMatchReportContext;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared Team and Player processors, run for each source over the same payloads. */
class FcttWebProcessorsTest {

    private static final Season SEASON = Season.of(2023);

    private InMemoryRepositories.Teams teams;
    private InMemoryRepositories.PlayerSeasons playerSeasons;

    @BeforeEach
    void setUp() {
        teams = new InMemoryRepositories.Teams();
        playerSeasons = new InMemoryRepositories.PlayerSeasons();
    }

    @Test
    void bcnesaTeamsAreStoredUnderTheirNormalisedName() {
        FcttWebTeamImportProcessor processor = new FcttWebTeamImportProcessor(ImportSource.BCNESA, teams);

        processor.process(bcnesa("acta_doubles.json", "CLUB ARIEL \"A\"", "CLUB ARIEL ''B''"));

        assertTrue(teams.findTeamByNameAndSeasonAndSource("CLUB ARIEL A", SEASON, ImportSource.BCNESA).isPresent());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("CLUB ARIEL B", SEASON, ImportSource.BCNESA).isPresent());
    }

    @Test
    void fcttTeamsKeepTheNameAsWrittenSoStoredTeamsAreStillFound() {
        FcttWebTeamImportProcessor processor = new FcttWebTeamImportProcessor(ImportSource.FCTT, teams);
        FcttMatchReportContext context = fctt("acta_doubles.json");

        processor.process(context);
        processor.process(context);

        assertTrue(teams.findTeamByNameAndSeasonAndSource("HUJASE JAÉN AUTÉNTICA", SEASON, ImportSource.FCTT)
                .isPresent());
        assertTrue(teams.findTeamByNameAndSeasonAndSource("MUSEO DE LA ALMENDRA FRANCISCO MORALES", SEASON,
                ImportSource.FCTT).isPresent());
        assertEquals("CTT \"A\"", FcttWebTeamImportProcessor.storedName(ImportSource.FCTT, "CTT \"A\""));
        assertEquals("CTT A", FcttWebTeamImportProcessor.storedName(ImportSource.BCNESA, "CTT \"A\""));
    }

    @Test
    void teamsOfOneSourceAreNeverVisibleToTheOther() {
        new FcttWebTeamImportProcessor(ImportSource.FCTT, teams).process(fctt("acta_doubles.json"));

        assertTrue(teams.findTeamByNameAndSeasonAndSource("HUJASE JAÉN AUTÉNTICA", SEASON, ImportSource.BCNESA)
                .isEmpty());
    }

    @Test
    void playersAreTheUnionOfLineupsDoublesAndGameParticipantsOncePerLicence() {
        FcttWebPlayerImportProcessor processor = new FcttWebPlayerImportProcessor(ImportSource.FCTT, playerSeasons);

        processor.process(fctt("acta_doubles.json"));
        processor.process(fctt("acta_doubles.json"));

        // Six lineup players, the same six again as declared doubles and as game participants.
        assertEquals(6, playerSeasons.findAllPlayerSeasonsBySource(ImportSource.FCTT).size());
        assertTrue(playerSeasons.findAllPlayerSeasonsBySource(ImportSource.BCNESA).isEmpty());
        assertTrue(playerSeasons.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.FCTT, "41528", SEASON)
                .isPresent());
    }

    @Test
    void bcnesaRegistersTheSamePlayersFromTheSamePayload() {
        new FcttWebPlayerImportProcessor(ImportSource.BCNESA, playerSeasons)
                .process(bcnesa("acta_doubles.json", "HOME", "AWAY"));

        assertEquals(6, playerSeasons.findAllPlayerSeasonsBySource(ImportSource.BCNESA).size());
        assertTrue(playerSeasons.findAllPlayerSeasonsBySource(ImportSource.FCTT).isEmpty());
    }

    @Test
    void aPlaceholderLicenceNeverRegistersAPlayerForEitherSource() {
        new FcttWebPlayerImportProcessor(ImportSource.FCTT, playerSeasons)
                .process(fctt("acta_fctt_placeholder_licences.json"));
        new FcttWebPlayerImportProcessor(ImportSource.BCNESA, playerSeasons)
                .process(bcnesa("acta_fctt_placeholder_licences.json", "HOME", "AWAY"));

        assertTrue(playerSeasons.findAllPlayerSeasonsBySource(ImportSource.FCTT).isEmpty());
        assertTrue(playerSeasons.findAllPlayerSeasonsBySource(ImportSource.BCNESA).isEmpty());
    }

    @Test
    void theDefaultOrientationOfASourceIsMirroredOnlyForFctt() {
        Acta acta = new ActaParser().parse(fixture("acta_doubles.json"));

        assertSame(ActaOrientation.MIRRORED, ActaOrientation.defaultFor(ImportSource.FCTT));
        assertSame(ActaOrientation.IDENTITY, ActaOrientation.defaultFor(ImportSource.BCNESA));
        assertSame(acta, ActaOrientation.IDENTITY.toHomeAway(acta));
    }

    private static FcttMatchReportContext fctt(String fixture) {
        Acta acta = new ActaParser().parse(fixture(fixture));
        return new FcttMatchReportContext("2023-2024", "male", "tercera-nacional", "G1", 1, fixture(fixture), acta);
    }

    private static BcnesaMatchReportContext bcnesa(String fixture, String home, String away) {
        Acta acta = new ActaParser().parse(fixture(fixture));
        return new BcnesaMatchReportContext("2023-2024", "Preferent", "G1", "1a Fase", 1, 0, home, away,
                fixture(fixture), acta, acta.games());
    }

    private static Path fixture(String name) {
        URL resource = FcttWebProcessorsTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
