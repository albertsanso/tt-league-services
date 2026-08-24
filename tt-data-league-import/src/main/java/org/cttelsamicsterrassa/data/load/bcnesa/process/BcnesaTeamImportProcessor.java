package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
@Order(BcnesaTeamImportProcessor.ORDER)
public class BcnesaTeamImportProcessor implements BcnesaMatchReportProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaTeamImportProcessor.class);

    private final TeamRepository teamRepository;

    @Inject
    public BcnesaTeamImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        Season season = context.toSeason();
        importTeam(context.homeTeamName(), season);
        importTeam(context.awayTeamName(), season);
    }

    private void importTeam(String rawName, Season season) {
        String name = BcnesaTeamNames.normalize(rawName);
        if (name == null) {
            return;
        }

        teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.BCNESA)
            .orElseGet(() -> {
                Team created = Team.createNew(ImportSource.BCNESA, name, season, null);
                teamRepository.saveTeam(created);
                LOGGER.debug("Created BCNESA team {} {}", name, season);
                return created;
            });
    }
}
