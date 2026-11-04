package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FcttPreviewValidationProcessorTest {

    @Test
    void publishedActaWithAFullLineupIsReadyToSimulate() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_female_groupless.json", "female",
                "copa-catalana-femenina-1a", null);

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("success", result.status().value());
        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("ready to simulate")));
        assertTrue(result.processingErrors().isEmpty());
    }

    @Test
    void unpublishedActaEmitsOnlyTheNotPublishedInfoMessage() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_unpublished.json", "male", "tercera-nacional", "G1");

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("success", result.status().value());
        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("not published")));
        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("stored as a scheduled match")));
        assertTrue(result.validationFindings().stream()
                .noneMatch(finding -> finding.message().contains("ready to simulate")));
        assertTrue(result.processingErrors().isEmpty());
    }

    @Test
    void noTeamPlaceholderIsReportedAsUnresolvedNotAsAnError() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_2026_no_team_placeholder.json", "female",
                "copa-catalana-femenina-1a", null);

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("success", result.status().value());
        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("no teams")
                        && finding.message().contains("not stored")));
        assertTrue(result.processingErrors().isEmpty(),
                "a placeholder must not fail the preview as incomplete teams used to");
    }

    @Test
    void invalidGroupFolderIsReportedOnlyWhenACompetitionHasAGroupFolder() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext withInvalidGroup = context("acta_fctt_female_groupless.json", "male",
                "tercera-nacional", "playoffs");

        new FcttPreviewValidationProcessor(collector).process(withInvalidGroup);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("failure", result.status().value());
        assertTrue(result.processingErrors().stream()
                .anyMatch(error -> error.message().contains("invalid group folder")));
    }

    @Test
    void groupLessCompetitionReportsNoInvalidGroupError() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_female_groupless.json", "female",
                "copa-catalana-femenina-1a", null);

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertTrue(result.processingErrors().stream()
                .noneMatch(error -> error.message().contains("invalid group folder")));
    }

    @Test
    void generoFolderMismatchIsReportedAsAnError() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_female_groupless.json", "male",
                "copa-catalana-femenina-1a", null);

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("failure", result.status().value());
        assertTrue(result.processingErrors().stream()
                .anyMatch(error -> error.message().contains("does not match")));
    }

    @Test
    void aDoublesPairListingTheSamePlayerTwiceIsReportedAsAWarning() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        FcttMatchReportContext context = context("acta_fctt_duplicate_doubles_player.json", "female",
                "copa-catalana-femenina-2a", "G2");

        new FcttPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("lists player CILLERO VIVERO, DELIA (licence 19214) twice")));
        assertTrue(result.validationFindings().stream()
                .noneMatch(finding -> finding.message().contains("licence 17807) twice")));
    }

    private static FcttMatchReportContext context(String fixture, String gender, String leagueCompetition, String group) {
        Path file = fixture(fixture);
        Acta acta = new ActaParser().parse(file);
        return new FcttMatchReportContext("2026-2027", gender, leagueCompetition, group, acta.round(), file, acta);
    }

    private static Path fixture(String name) {
        URL resource = FcttPreviewValidationProcessorTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
