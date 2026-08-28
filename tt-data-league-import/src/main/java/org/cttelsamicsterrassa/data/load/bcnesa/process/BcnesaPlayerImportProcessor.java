package org.cttelsamicsterrassa.data.load.bcnesa.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
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

import javax.inject.Inject;

@Component
@Order(BcnesaPlayerImportProcessor.ORDER)
public class BcnesaPlayerImportProcessor implements BcnesaMatchReportProcessor {

    /** Players come after clubs and before matches, which reference both. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(BcnesaPlayerImportProcessor.class);

    private final PlayerSeasonRepository playerSeasonRepository;

    @Inject
    public BcnesaPlayerImportProcessor(PlayerSeasonRepository playerSeasonRepository) {
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
        importPlayerSeason(participant.name(), participant.licenseId(), season, context);
    }

    private void importDoublesParticipants(ActaParticipant participant,
                                           Season season,
                                           BcnesaMatchReportContext context) {
        if (participant == null) {
            return;
        }
        for (ActaLineupPlayer player : participant.doublesPlayers()) {
            if (player != null) {
                importPlayerSeason(player.name(), player.licenseId(), season, context);
            }
        }
    }

    private void importPlayerSeason(String name, String license, Season season, BcnesaMatchReportContext context) {
        if (isBlank(name) || isBlank(license)) {
            LOGGER.warn("Skipping participant without name or licence in {}", context.matchReportFile());
            return;
        }

        playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.BCNESA, license, season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(
                            ImportSource.BCNESA, name, license, null, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created BCNESA player season {} {} ({})", name, season, license);
                    return created;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
