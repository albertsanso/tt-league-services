package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
@Order(RfetmTeamImportProcessor.ORDER)
public class RfetmTeamImportProcessor implements MatchContextProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmTeamImportProcessor.class);

    private final TeamRepository teamRepository;

    @Inject
    public RfetmTeamImportProcessor(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public void process(MatchReportContext context) {
        Season season = context.toSeason();
        importTeam(context.homeTeam(), homeTeam(context), season);
        importTeam(context.awayTeam(), awayTeam(context), season);
    }

    private void importTeam(RfetmClubKey key, ActaTeam team, Season season) {
        String name = team != null ? team.name() : key.name();

        teamRepository.findTeamByNameAndSeasonAndSource(name, season, ImportSource.RFETM)
            .orElseGet(() -> {
                Team created = Team.createNew(ImportSource.RFETM, name, season, null);
                teamRepository.saveTeam(created);
                LOGGER.debug("Created team {} {} ({})", name, season, key);
                return created;
            });
    }

    private static ActaTeam homeTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().home();
    }

    private static ActaTeam awayTeam(MatchReportContext context) {
        return context.acta() == null || context.acta().teams() == null ? null : context.acta().teams().away();
    }
}
