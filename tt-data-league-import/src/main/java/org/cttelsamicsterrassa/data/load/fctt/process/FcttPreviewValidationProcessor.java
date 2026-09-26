package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.preview.ActaPreviewValidationSupport;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

import java.util.Map;

public class FcttPreviewValidationProcessor implements FcttMatchReportProcessor {

    private final ImportPreviewCollector collector;

    public FcttPreviewValidationProcessor(ImportPreviewCollector collector) {
        this.collector = collector;
    }

    @Override
    public void process(FcttMatchReportContext context) {
        Acta acta = context.acta();
        String location = ActaPreviewValidationSupport.location(context.matchReportFile());
        if (acta.teams() == null || acta.teams().home() == null || acta.teams().away() == null) {
            collector.error("FCTT report has incomplete teams.", location);
        } else {
            validateTeam(acta.teams().home().name(), "home", location);
            validateTeam(acta.teams().away().name(), "away", location);
        }
        if (context.hasGroupFolder() && context.groupNumber().isEmpty()) {
            collector.error("FCTT report has an invalid group folder: " + context.group(), location);
        }
        if (acta.gender() != null && !acta.gender().equals(context.sex())) {
            collector.error("FCTT report gender \"%s\" does not match the %s folder.".formatted(acta.gender(),
                    context.gender()), location);
        }

        if (!acta.isPublished()) {
            collector.info("FCTT acta not published; fixture will not be stored as a match.", location);
            return;
        }

        if (acta.abcIsHome() == null) {
            collector.warning("FCTT report has no abc_es_local value.", location);
        }
        if (acta.games().isEmpty()) {
            collector.warning("FCTT report has no games.", location);
        }
        if (acta.lineups() == null) {
            collector.warning("FCTT report has no lineups; player and game details may be incomplete.", location);
        } else {
            validateLineupSize(acta.lineups().home(), "home", location);
            validateLineupSize(acta.lineups().away(), "away", location);
            ActaPreviewValidationSupport.validateLineupPlayers(
                    acta.lineups().home(), collector, context.matchReportFile(), "FCTT");
            ActaPreviewValidationSupport.validateLineupPlayers(
                    acta.lineups().away(), collector, context.matchReportFile(), "FCTT");
        }
        if (acta.doubles() != null) {
            ActaPreviewValidationSupport.validateDoublesPlayers(
                    acta.doubles().home(), collector, context.matchReportFile(), "FCTT");
            ActaPreviewValidationSupport.validateDoublesPlayers(
                    acta.doubles().away(), collector, context.matchReportFile(), "FCTT");
        }
        ActaPreviewValidationSupport.validateGameParticipants(
                acta.games(), collector, context.matchReportFile(), "FCTT");
        collector.info("FCTT report is ready to simulate: %d game(s).".formatted(acta.games().size()), location);
    }

    private void validateLineupSize(Map<String, ?> lineup, String side, String location) {
        if (lineup.size() != 3) {
            collector.warning("FCTT %s lineup has %d player(s); expected 3.".formatted(side, lineup.size()), location);
        }
    }

    private void validateTeam(String name, String side, String location) {
        if (ActaPreviewValidationSupport.isBlank(name)) {
            collector.error("FCTT %s team has no name.".formatted(side), location);
        }
    }
}
