package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.preview.ActaPreviewValidationSupport;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

import java.util.stream.Stream;

public class RfetmPreviewValidationProcessor implements MatchContextProcessor {

    private final ImportPreviewCollector collector;

    public RfetmPreviewValidationProcessor(ImportPreviewCollector collector) {
        this.collector = collector;
    }

    @Override
    public void process(MatchReportContext context) {
        Acta acta = context.acta();
        String location = ActaPreviewValidationSupport.location(context.matchReportFile());
        if (acta == null) {
            collector.error("RFETM report has no parsed payload.", location);
            return;
        }

        validateTeam(context.homeTeam(), "home", location);
        validateTeam(context.awayTeam(), "away", location);
        if (acta.lineups() == null) {
            collector.warning("RFETM report has no lineups; player and game details may be incomplete.", location);
        } else {
            ActaPreviewValidationSupport.validateLineupPlayers(
                    acta.lineups().home(), collector, context.matchReportFile(), "RFETM");
            ActaPreviewValidationSupport.validateLineupPlayers(
                    acta.lineups().away(), collector, context.matchReportFile(), "RFETM");
        }
        if (acta.doubles() != null) {
            ActaPreviewValidationSupport.validateDoublesPlayers(
                    acta.doubles().home(), collector, context.matchReportFile(), "RFETM");
            ActaPreviewValidationSupport.validateDoublesPlayers(
                    acta.doubles().away(), collector, context.matchReportFile(), "RFETM");
        }
        ActaPreviewValidationSupport.validateGameParticipants(
                acta.games(), collector, context.matchReportFile(), "RFETM");

        long playerCount = Stream.concat(
                        acta.lineups() == null ? Stream.empty() : acta.lineups().home().values().stream(),
                        acta.lineups() == null ? Stream.empty() : acta.lineups().away().values().stream())
                .filter(player -> player != null && !ActaPreviewValidationSupport.isBlank(player.licenseId()))
                .count();
        collector.info("RFETM report is ready to simulate: %d lineup player(s), %d game(s)."
                        .formatted(playerCount, acta.games().size()),
                location);
    }

    private void validateTeam(RfetmClubKey team, String side, String location) {
        if (team == null || (ActaPreviewValidationSupport.isBlank(team.rfetmId())
                && ActaPreviewValidationSupport.isBlank(team.name()))) {
            collector.error("RFETM %s team has no resolvable federation id or name.".formatted(side), location);
        }
    }
}
