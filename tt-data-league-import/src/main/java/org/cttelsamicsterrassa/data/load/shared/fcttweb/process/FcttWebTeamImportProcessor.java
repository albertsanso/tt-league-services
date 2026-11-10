package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.bcnesa.process.BcnesaTeamNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Registers the two clubs of a fctt.cat fixture as season {@link Team}s of one {@link ImportSource}.
 *
 * <p>Clubs resolve by name within the source and season. BCNESA names are normalised first
 * ({@link BcnesaTeamNames}), because its export spells the same team's letter suffix several ways.
 * FCTT names are used as written: normalising them would stop existing stored FCTT teams from being
 * found again, and the two sources never share rows ({@code source} is part of the lookup).</p>
 */
public class FcttWebTeamImportProcessor {

    /** Clubs come first: players and matches both reference them. */
    public static final int ORDER = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttWebTeamImportProcessor.class);

    private final ImportSource source;
    private final TeamRepository teamRepository;

    public FcttWebTeamImportProcessor(ImportSource source, TeamRepository teamRepository) {
        this.source = Objects.requireNonNull(source, "source");
        this.teamRepository = teamRepository;
    }

    public void process(FcttWebFixtureContext context) {
        Season season = context.toSeason();
        importTeam(context.homeTeamName(), season, context);
        importTeam(context.awayTeamName(), season, context);
    }

    /** The team name this source stores and looks up for {@code rawName}. */
    public static String storedName(ImportSource source, String rawName) {
        return source == ImportSource.BCNESA ? BcnesaTeamNames.normalize(rawName) : rawName;
    }

    private void importTeam(String rawName, Season season, FcttWebFixtureContext context) {
        String name = storedName(source, rawName);
        if (name == null || name.isBlank()) {
            LOGGER.warn("Skipping {} team without a name in {}", source, context.matchReportFile());
            return;
        }
        teamRepository.findTeamByNameAndSeasonAndSource(name, season, source)
                .orElseGet(() -> {
                    Team created = Team.createNew(source, name, season, null);
                    teamRepository.saveTeam(created);
                    LOGGER.debug("Created {} team {} {}", source, name, season);
                    return created;
                });
    }
}
