package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaLineupPlayer;
import org.cttelsamicsterrassa.data.load.shared.process.MatchReportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Stores the players named in a match report's lineups, and their registration for that season.
 *
 * <p>A season registration is keyed by federation licence, which is the identifier the reports
 * carry. The season-independent {@code FEDERATED_PLAYER} row is keyed by name, as the data model defines it —
 * so two licences under one spelling share a player, and a player whose name is spelled differently
 * in two seasons gets two rows. Doubles-pair members are also imported because their name and
 * licence can identify players omitted from {@code alineaciones}.</p>
 */
@Component
@Order(RfetmPlayerImportProcessor.ORDER)
public class RfetmPlayerImportProcessor implements MatchContextProcessor {

    /** Players come after clubs and before matches, which reference both. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmPlayerImportProcessor.class);

    private final FederatedPlayerRepository playerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;

    public RfetmPlayerImportProcessor(FederatedPlayerRepository playerRepository,
                                 PlayerSeasonRepository playerSeasonRepository) {
        this.playerRepository = playerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
    }

    @Override
    public void process(MatchReportContext context) {
        Acta acta = context.acta();
        if (acta == null) {
            LOGGER.warn("No match report in {}; no players imported", context.matchReportFile());
            return;
        }

        Season season = context.toSeason();
        for (ActaLineupPlayer player : players(acta)) {
            importPlayer(player, season, context);
        }
    }

    private void importPlayer(ActaLineupPlayer lineupPlayer, Season season, MatchReportContext context) {
        String name = lineupPlayer.name();
        String license = lineupPlayer.license();
        if (isBlank(name) || isBlank(license)) {
            LOGGER.warn("Skipping lineup entry without name or licence in {}", context.matchReportFile());
            return;
        }

        playerSeasonRepository.findPlayerSeasonByLicenseAndSeason(ImportSource.RFETM, license, season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(ImportSource.RFETM, name, license, null, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created player season {} {} ({})", name, season, license);
                    return created;
                });
    }

    private static List<ActaLineupPlayer> players(Acta acta) {
        Stream<ActaLineupPlayer> lineupPlayers = acta.lineups() == null
                ? Stream.empty()
                : Stream.concat(acta.lineups().home().values().stream(), acta.lineups().away().values().stream());
        Stream<ActaLineupPlayer> declaredDoublesPlayers = acta.doubles() == null
                ? Stream.empty()
                : Stream.concat(acta.doubles().home().stream(), acta.doubles().away().stream());
        Stream<ActaLineupPlayer> gameDoublesPlayers = acta.games().stream()
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
