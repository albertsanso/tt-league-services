package org.cttelsamicsterrassa.data.load.fctt.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.ActaLineupPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Stores FCTT lineup and doubles players, together with their FCTT season registrations.
 */
@Component
@Order(FcttPlayerImportProcessor.ORDER)
public class FcttPlayerImportProcessor implements FcttMatchReportProcessor {

    /** Players are stored after clubs and before matches. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(FcttPlayerImportProcessor.class);

    private final PlayerRepository playerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    public FcttPlayerImportProcessor(PlayerRepository playerRepository,
                                     PlayerSeasonRepository playerSeasonRepository) {
        this.playerRepository = playerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public void process(FcttMatchReportContext context) {
        Season season = context.toSeason();
        for (ActaLineupPlayer player : players(context)) {
            importPlayer(player, season, context);
        }
    }

    private void importPlayer(ActaLineupPlayer lineupPlayer, Season season, FcttMatchReportContext context) {
        if (isBlank(lineupPlayer.name()) || isBlank(lineupPlayer.license())) {
            LOGGER.warn("Skipping FCTT player without name or licence in {}", context.matchReportFile());
            return;
        }

        Player player = playerRepository.findPlayerBySourceAndName(ImportSource.FCTT, lineupPlayer.name())
                .orElseGet(() -> {
                    Player created = Player.createNew(ImportSource.FCTT, lineupPlayer.name());
                    playerRepository.savePlayer(created);
                    LOGGER.debug("Created FCTT player {}", lineupPlayer.name());
                    return created;
                });

        playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.FCTT, lineupPlayer.license(), season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(
                            ImportSource.FCTT, lineupPlayer.name(), lineupPlayer.license(), player, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created FCTT player season {} {} ({})",
                            lineupPlayer.name(), season, lineupPlayer.license());
                    return created;
                });
    }

    private static List<ActaLineupPlayer> players(FcttMatchReportContext context) {
        Stream<ActaLineupPlayer> lineupPlayers = context.acta().lineups() == null
                ? Stream.empty()
                : Stream.concat(context.acta().lineups().home().values().stream(),
                        context.acta().lineups().away().values().stream());
        Stream<ActaLineupPlayer> declaredDoublesPlayers = context.acta().doubles() == null
                ? Stream.empty()
                : Stream.concat(context.acta().doubles().home().stream(), context.acta().doubles().away().stream());
        Stream<ActaLineupPlayer> gameDoublesPlayers = context.acta().games().stream()
                .filter(ActaGame::isDoubles)
                .flatMap(game -> Stream.of(game.home(), game.away()))
                .filter(Objects::nonNull)
                .flatMap(participant -> participant.doublesPlayers().stream());

        return Stream.of(lineupPlayers, declaredDoublesPlayers, gameDoublesPlayers)
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
