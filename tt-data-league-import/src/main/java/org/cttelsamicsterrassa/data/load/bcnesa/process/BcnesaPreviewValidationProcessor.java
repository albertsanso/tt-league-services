package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.load.shared.preview.ActaPreviewValidationSupport;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

public class BcnesaPreviewValidationProcessor implements BcnesaMatchReportProcessor {

    private final ImportPreviewCollector collector;

    public BcnesaPreviewValidationProcessor(ImportPreviewCollector collector) {
        this.collector = collector;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        String location = ActaPreviewValidationSupport.location(context.matchReportFile());
        if (!context.isResolved()) {
            collector.error("BCNESA fixture clubs could not be attributed.", location);
            return;
        }

        String homeName = BcnesaTeamNames.normalize(context.homeTeamName());
        String awayName = BcnesaTeamNames.normalize(context.awayTeamName());
        if (ActaPreviewValidationSupport.isBlank(homeName)) {
            collector.error("BCNESA home team has no resolvable name.", location);
        }
        if (ActaPreviewValidationSupport.isBlank(awayName)) {
            collector.error("BCNESA away team has no resolvable name.", location);
        }

        ActaPreviewValidationSupport.validateGameParticipants(
                context.games(), collector, context.matchReportFile(), "BCNESA");
        collector.info("BCNESA fixture %d is ready to simulate: %s vs %s, %d game(s)."
                        .formatted(context.fixtureIndex(), homeName, awayName, context.games().size()),
                location);
    }
}
