package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
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

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@Order(RfetmPlayerImportProcessor.ORDER)
public class RfetmPlayerImportProcessor implements MatchContextProcessor {

    /** Players come after clubs and before matches, which reference both. */
    public static final int ORDER = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmPlayerImportProcessor.class);

    private final PlayerSeasonRepository playerSeasonRepository;

    @Inject
    public RfetmPlayerImportProcessor(PlayerSeasonRepository playerSeasonRepository) {
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

        playerSeasonRepository.findPlayerSeasonBySourceLicenseAndSeason(ImportSource.RFETM, license, season)
                .orElseGet(() -> {
                    PlayerSeason created = PlayerSeason.createNew(
                            ImportSource.RFETM, name, license, null, season);
                    playerSeasonRepository.savePlayerSeason(created);
                    LOGGER.debug("Created player season {} {} ({})", name, season, license);
                    return created;
                });
    }

    private FederatedPlayer linkCanonicalPlayer(FederatedPlayer federatedPlayer, Player canonicalPlayer) {
        return canonicalPlayer == null || federatedPlayer.getPlayer().isPresent()
                ? federatedPlayer
                : federatedPlayer.withPlayer(canonicalPlayer);
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
