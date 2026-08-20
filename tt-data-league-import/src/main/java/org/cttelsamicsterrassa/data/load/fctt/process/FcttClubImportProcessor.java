package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stores the FCTT report's two clubs and their season entries.
 *
 * <p>FCTT club lookup is explicitly scoped to {@link ImportSource#FCTT}. Team ids in the shared
 * payload are RFETM-shaped fields and therefore are not used as FCTT club identity.</p>
 */
@Component
@Order(FcttClubImportProcessor.ORDER)
public class FcttClubImportProcessor implements FcttMatchReportProcessor {

    /** Clubs must exist before player and match processors run. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttClubImportProcessor.class);

    private final TeamRepository teamRepository;

    public FcttClubImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public void process(FcttMatchReportContext context) {
        if (context.acta().teams() == null) {
            LOGGER.warn("No teams in {}; no clubs imported", context.matchReportFile());
            return;
        }

        Season season = context.toSeason();
        importClub(context.acta().teams().home(), season, context);
        importClub(context.acta().teams().away(), season, context);
    }

    private void importClub(ActaTeam team, Season season, FcttMatchReportContext context) {
        if (team == null || isBlank(team.name())) {
            LOGGER.warn("Skipping FCTT team without a name in {}", context.matchReportFile());
            return;
        }
        teamRepository.findTeamByNameAndSeasonAndSource(team.name(), season, ImportSource.FCTT)
                .orElseGet(() -> {
                    Team created = Team.createNew(ImportSource.FCTT, team.name(), season, null);
                    teamRepository.saveTeam(created);
                    LOGGER.debug("Created FCTT team {} {}", team.name(), season);
                    return created;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
