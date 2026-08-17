package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaTeam;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stores the two clubs of a match report, and their entry for that season.
 *
 * <p>Clubs are resolved by {@link RfetmClubKey}: the RFETM team id where the payload carries one, and
 * the team name scoped to season and competition where it does not. A bare name is never the key -
 * names drift across seasons and one name is normally shared by the A, B and C teams of a club, so an
 * unscoped name upsert would merge teams that must stay apart. Scoping to the competition is what
 * keeps them distinct; see {@link RfetmClubKey} for the measurement behind that choice.</p>
 *
 * <p>A club keeps the name it was first seen under; the name as written in a given season is
 * recorded on that season's {@code CLUB_SEASON} row instead.</p>
 */
@Component
@Order(RfetmClubImportProcessor.ORDER)
public class RfetmClubImportProcessor implements MatchReportProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmClubImportProcessor.class);

    private final ClubRepository clubRepository;
    private final ClubSeasonRepository clubSeasonRepository;

    public RfetmClubImportProcessor(ClubRepository clubRepository, ClubSeasonRepository clubSeasonRepository) {
        this.clubRepository = clubRepository;
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public void process(MatchReportContext context) {
        Season season = context.toSeason();
        importClub(context.homeClub(), homeTeam(context), season);
        importClub(context.awayClub(), awayTeam(context), season);
    }

    private void importClub(RfetmClubKey key, ActaTeam team, Season season) {
        String name = team != null ? team.name() : key.name();

        Club club = clubRepository.findClubBySourceAndName(ImportSource.RFETM, name)
                .orElseGet(() -> {
                    Club created = Club.createNew(ImportSource.RFETM, name);
                    clubRepository.saveClub(created);
                    LOGGER.debug("Created club {} ({})", name, key);
                    return created;
                });

        clubSeasonRepository.findClubSeasonByNameAndSeasonAndSource(name, season, ImportSource.RFETM);
        clubSeasonRepository.findClubSeasonByClubAndSeasonAndSource(club.getId(), season, ImportSource.RFETM.name())
                .orElseGet(() -> {
                    ClubSeason created = ClubSeason.createNew(ImportSource.RFETM, name, season, club);
                    clubSeasonRepository.saveClubSeason(created);
                    LOGGER.debug("Created club season {} {} ({})", name, season, key);
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
