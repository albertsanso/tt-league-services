package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stores the players named in a BCNESA fixture's singles games, and their registration for that
 * season.
 *
 * <p>Unlike the RFETM import, this reads players from {@code partidos} rather than
 * {@code alineaciones}: a BCNESA file's {@code alineaciones} only ever covers the file's first
 * fixture (six entries, always), while every singles game in every fixture carries its own
 * participant licence and name. Reading from the games therefore covers every fixture, not just the
 * first.</p>
 *
 * <p>Doubles participants ({@code partidos[].tipo == "dobles"}) are imported too, allowing the
 * match processor to retain a pair member who does not appear in a singles game.</p>
 */
@Component
@Order(BcnesaPlayerImportProcessor.ORDER)
public class BcnesaPlayerImportProcessor implements BcnesaMatchReportProcessor {

    /** Players come after clubs and before matches, which reference both. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaPlayerImportProcessor.class);

    private final PlayerRepository playerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    public BcnesaPlayerImportProcessor(PlayerRepository playerRepository,
                                       PlayerSeasonRepository playerSeasonRepository) {
        this.playerRepository = playerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public void process(BcnesaMatchReportContext context) {
        Season season = context.toSeason();
        for (ActaGame game : context.games()) {
            if (game.isDoubles()) {
                importDoublesParticipants(game.home(), season, context);
                importDoublesParticipants(game.away(), season, context);
                continue;
            }
            importParticipant(game.home(), season, context);
            importParticipant(game.away(), season, context);
        }
    }

    private void importParticipant(ActaParticipant participant, Season season, BcnesaMatchReportContext context) {
        if (participant == null) {
            return;
        }
        importPlayer(participant.name(), participant.license(), season, context);
    }

    private void importDoublesParticipants(ActaParticipant participant,
                                           Season season,
                                           BcnesaMatchReportContext context) {
        if (participant == null) {
            return;
        }
        for (ActaLineupPlayer player : participant.doublesPlayers()) {
            if (player != null) {
                importPlayer(player.name(), player.license(), season, context);
            }
        }
    }

    private void importPlayer(String name, String license, Season season, BcnesaMatchReportContext context) {
        if (isBlank(name) || isBlank(license)) {
            LOGGER.warn("Skipping participant without name or licence in {}", context.matchReportFile());
            return;
        }

        playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.BCNESA, license, season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(ImportSource.BCNESA, name, license, null, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created BCNESA player season {} {} ({})", name, season, license);
                    return created;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
