package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaClassification;
import org.cttelsamicsterrassa.data.load.shared.classify.ActaCompletenessClassifier;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.preview.ActaPreviewValidationSupport;
import org.cttelsamicsterrassa.data.load.shared.preview.ImportPreviewCollector;

import java.util.Map;
import java.util.Objects;

/**
 * Read-only validation of a fctt.cat fixture for the import preview. It reports what would stop the
 * fixture from being stored or would store it incompletely, and never writes.
 *
 * <p>The checks follow what each source's match processor depends on: a BCNESA fixture needs both
 * attributed clubs and well-formed game participants; an FCTT report needs both team objects, a
 * numeric group folder, a payload gender that agrees with its folder and, once published, lineups,
 * doubles and game participants.</p>
 */
public class FcttWebPreviewValidationProcessor {

    private final ImportSource source;
    private final ImportPreviewCollector collector;
    private final ActaCompletenessClassifier classifier = new ActaCompletenessClassifier();

    public FcttWebPreviewValidationProcessor(ImportSource source, ImportPreviewCollector collector) {
        this.source = Objects.requireNonNull(source, "source");
        this.collector = collector;
    }

    public void process(FcttWebFixtureContext context) {
        if (source == ImportSource.FCTT) {
            validateFctt(context);
        } else {
            validateBcnesa(context);
        }
    }

    private void validateBcnesa(FcttWebFixtureContext context) {
        String location = ActaPreviewValidationSupport.location(context.matchReportFile());
        if (!context.isResolved()) {
            collector.error("BCNESA fixture clubs could not be attributed.", location);
            return;
        }

        String homeName = FcttWebTeamImportProcessor.storedName(source, context.homeTeamName());
        String awayName = FcttWebTeamImportProcessor.storedName(source, context.awayTeamName());
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

    private void validateFctt(FcttWebFixtureContext context) {
        Acta acta = context.acta();
        String location = ActaPreviewValidationSupport.location(context.matchReportFile());
        ActaClassification classification = classifier.classify(acta);
        if (classification.unresolvedPendingFixture()) {
            collector.info("FCTT pending fixture has no teams; it will be reported as unresolved "
                    + "and not stored.", location);
        } else if (acta.teams() == null || acta.teams().home() == null || acta.teams().away() == null) {
            collector.error("FCTT report has incomplete teams.", location);
        } else {
            validateTeam(acta.teams().home().name(), "home", location);
            validateTeam(acta.teams().away().name(), "away", location);
        }
        if (context.hasInvalidGroupFolder()) {
            collector.error("FCTT report has an invalid group folder: " + context.group(), location);
        }
        if (acta.gender() != null && !acta.gender().equals(context.sex())) {
            collector.error("FCTT report gender \"%s\" does not match the %s folder.".formatted(acta.gender(),
                    context.gender()), location);
        }

        if (!acta.isPublished()) {
            collector.info("FCTT acta not published; fixture will be stored as a scheduled match.", location);
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
