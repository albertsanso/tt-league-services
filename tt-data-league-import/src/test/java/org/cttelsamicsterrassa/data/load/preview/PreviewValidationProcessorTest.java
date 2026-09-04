package org.cttelsamicsterrassa.data.load.preview;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaMatchReportContext;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmClubKey;
import org.cttelsamicsterrassa.data.load.rfetm.process.RfetmPreviewValidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParser;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewValidationProcessorTest {

    @Test
    void rfetmPreviewCollectsFindingsWithoutWritingThroughRepositories() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        Acta acta = new ActaParser().parse(fixture("acta_singles.json"));
        MatchReportContext context = new MatchReportContext(
                "2023-2024",
                "super-divisio",
                "1",
                "masculino",
                RfetmClubKey.ofFederationId("193", "HORTITEC ALZIRA TT"),
                RfetmClubKey.ofFederationId("23", "CERLALCOR ALCOI"),
                fixture("acta_singles.json"),
                acta);

        new RfetmPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("success", result.status().value());
        assertTrue(result.validationFindings().stream()
                .anyMatch(finding -> finding.message().contains("lineup player")));
        assertTrue(result.processingErrors().isEmpty());
    }

    @Test
    void bcnesaPreviewReportsUnresolvedTeamsAsProcessingErrors() {
        ImportPreviewCollector collector = new ImportPreviewCollector();
        Acta acta = new ActaParser().parse(fixture("acta_matchday.json"));
        BcnesaMatchReportContext context = new BcnesaMatchReportContext(
                "2020-2021",
                "Preferent",
                "G1",
                "1a Fase",
                7,
                0,
                null,
                "CTT DELS HORTS",
                fixture("acta_matchday.json"),
                acta,
                acta.games());

        new BcnesaPreviewValidationProcessor(collector).process(context);
        ImportPreviewResult result = collector.toResult(1, 1, 0, 0);

        assertEquals("failure", result.status().value());
        assertNotNull(result.processingErrors().getFirst().message());
    }

    private static Path fixture(String name) {
        URL resource = PreviewValidationProcessorTest.class.getResource("/actas/" + name);
        assertNotNull(resource, () -> "Missing test fixture " + name);
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
