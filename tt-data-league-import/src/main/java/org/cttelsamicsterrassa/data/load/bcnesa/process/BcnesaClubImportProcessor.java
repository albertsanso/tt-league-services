package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stores the two clubs of a BCNESA fixture, and their entry for that season.
 *
 * <p>BCNESA carries no team id ({@code equipos.*.id} is null throughout the export), so clubs resolve
 * by {@link BcnesaClubNames#normalize(String) normalized name} scoped to
 * {@link ImportSource#BCNESA}, never to name alone: a handful of names coincide with RFETM clubs by
 * chance without being the same entity, and the two federations must never merge on that basis.</p>
 */
@Component
@Order(BcnesaClubImportProcessor.ORDER)
public class BcnesaClubImportProcessor implements BcnesaMatchReportProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaClubImportProcessor.class);

    private final ClubSeasonRepository clubSeasonRepository;

    public BcnesaClubImportProcessor(ClubSeasonRepository clubSeasonRepository) {
        this.clubSeasonRepository = clubSeasonRepository;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        Season season = context.toSeason();
        importClub(context.homeClubName(), season);
        importClub(context.awayClubName(), season);
    }

    private void importClub(String rawName, Season season) {
        String name = BcnesaClubNames.normalize(rawName);
        if (name == null) {
            return;
        }

        clubSeasonRepository.findClubSeasonByNameAndSeasonAndSource(name, season, ImportSource.BCNESA)
                .orElseGet(() -> {
                    ClubSeason created = ClubSeason.createNew(ImportSource.BCNESA, name, season, null);
                    clubSeasonRepository.saveClubSeason(created);
                    LOGGER.debug("Created BCNESA club season {} {}", name, season);
                    return created;
                });
    }
}
