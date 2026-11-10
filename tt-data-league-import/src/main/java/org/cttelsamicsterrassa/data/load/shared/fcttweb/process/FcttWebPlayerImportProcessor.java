package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the players of a fctt.cat fixture as season {@link PlayerSeason}s of one
 * {@link ImportSource}, identified by licence.
 *
 * <p>The players of a fixture are the union of the lineups, the declared doubles pairs and the
 * participants of the fixture's own games (singles and doubles members), de-duplicated by licence.
 * A player without a name or without a {@linkplain FcttWebLicenses#isUsable usable} licence is
 * logged and skipped, never registered under a placeholder licence.</p>
 */
public class FcttWebPlayerImportProcessor {

    /** Players come after clubs and before matches, which reference both. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttWebPlayerImportProcessor.class);

    private final ImportSource source;
    private final PlayerSeasonRepository playerSeasonRepository;

    public FcttWebPlayerImportProcessor(ImportSource source, PlayerSeasonRepository playerSeasonRepository) {
        this.source = Objects.requireNonNull(source, "source");
        this.playerSeasonRepository = playerSeasonRepository;
    }

    public void process(FcttWebFixtureContext context) {
        Season season = context.toSeason();
        for (Registration registration : registrations(context)) {
            importPlayerSeason(registration, season, context);
        }
    }

    private void importPlayerSeason(Registration player, Season season, FcttWebFixtureContext context) {
        playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(source, player.license(), season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(source, player.name(), player.license(), null, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created {} player season {} {} ({})", source, player.name(), season, player.license());
                    return created;
                });
    }

    /** The registrable players of {@code context}, first occurrence per licence, in file order. */
    private List<Registration> registrations(FcttWebFixtureContext context) {
        Map<String, Registration> byLicense = new LinkedHashMap<>();
        for (Registration candidate : candidates(context)) {
            if (candidate.name() == null || candidate.name().isBlank()
                    || !FcttWebLicenses.isUsable(candidate.license())) {
                LOGGER.warn("Skipping {} player \"{}\" without name or usable licence ({}) in {}",
                        source, candidate.name(), candidate.license(), context.matchReportFile());
                continue;
            }
            byLicense.putIfAbsent(candidate.license(), candidate);
        }
        return new ArrayList<>(byLicense.values());
    }

    private static List<Registration> candidates(FcttWebFixtureContext context) {
        Acta acta = context.acta();
        List<Registration> candidates = new ArrayList<>();
        if (acta.lineups() != null) {
            acta.lineups().home().values().forEach(player -> add(candidates, player));
            acta.lineups().away().values().forEach(player -> add(candidates, player));
        }
        if (acta.doubles() != null) {
            acta.doubles().home().forEach(player -> add(candidates, player));
            acta.doubles().away().forEach(player -> add(candidates, player));
        }
        for (ActaGame game : context.games()) {
            for (ActaParticipant participant : new ActaParticipant[]{game.home(), game.away()}) {
                if (participant == null) {
                    continue;
                }
                if (game.isDoubles()) {
                    participant.doublesPlayers().forEach(player -> add(candidates, player));
                } else {
                    candidates.add(new Registration(participant.name(), participant.licenseId()));
                }
            }
        }
        return candidates;
    }

    private static void add(List<Registration> candidates, ActaLineupPlayer player) {
        if (player != null) {
            candidates.add(new Registration(player.name(), player.licenseId()));
        }
    }

    private record Registration(String name, String license) {
    }
}
