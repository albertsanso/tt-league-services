package org.cttelsamicsterrassa.data.load.shared.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.player.consolidate.PlayerSeasonConsolidationProcessor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSeasonConsolidationProcessorTest {

    @Test
    void representsAnUnassignedPlayerSeasonWithAnEmptyOptional() {
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "PLAYER", "1", null, Season.of(2023));

        assertTrue(registration.getFederatedPlayer().isEmpty());
        assertSame(registration, registration.withFederatedPlayer(null));
    }

    @Test
    void preservesRegistrationWhenReplacingOrReusingPlayerAssociation() {
        FederatedPlayer first = FederatedPlayer.createNew(ImportSource.FCTT, "PLAYER");
        FederatedPlayer second = FederatedPlayer.createNew(ImportSource.FCTT, "PLAYER");
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "PLAYER", "1", first, Season.of(2023));

        assertSame(registration, registration.withFederatedPlayer(first));
        PlayerSeason replaced = registration.withFederatedPlayer(second);

        assertEquals(second.getId(), replaced.getFederatedPlayer().orElseThrow().getId());
        assertEquals(registration.getId(), replaced.getId());
        assertEquals(registration.getLicense(), replaced.getLicense());
        assertEquals(registration.getSeason(), replaced.getSeason());
    }

    @Test
    void consolidatesExactNamesWithoutChangingRegistrationIdentity() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        InMemoryRepositories.PlayerSeasons seasons = new InMemoryRepositories.PlayerSeasons();
        PlayerSeason first = save(seasons, "John Doe", "1", Season.of(2023), null);
        PlayerSeason second = save(seasons, "DOE, JOHN", "2", Season.of(2024), null);

        PlayerConsolidationSummary summary = new PlayerSeasonConsolidationProcessor(players, seasons)
                .consolidate(ImportSource.FCTT);

        assertEquals(1, summary.exactGroups());
        assertEquals(1, summary.playersCreated());
        assertEquals(first.getId(), seasons.findPlayerSeasonById(first.getId()).orElseThrow().getId());
        assertEquals(second.getId(), seasons.findPlayerSeasonById(second.getId()).orElseThrow().getId());
        assertEquals("1", seasons.findPlayerSeasonById(first.getId()).orElseThrow().getLicense());
        assertEquals("2", seasons.findPlayerSeasonById(second.getId()).orElseThrow().getLicense());
        assertEquals(1, players.byId.size());
        assertEquals(seasons.findPlayerSeasonById(first.getId()).orElseThrow().getFederatedPlayer().orElseThrow().getId(),
                seasons.findPlayerSeasonById(second.getId()).orElseThrow().getFederatedPlayer().orElseThrow().getId());
    }

    @Test
    void isolatesSourcesAndIsIdempotent() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        InMemoryRepositories.PlayerSeasons seasons = new InMemoryRepositories.PlayerSeasons();
        save(seasons, "Jane Doe", "1", Season.of(2023), null);
        save(seasons, "Jane Doe", "2", Season.of(2024), null);
        PlayerSeason otherSource = PlayerSeason.createNew(ImportSource.BCNESA, "Jane Doe", "4", null, Season.of(2023));
        seasons.savePlayerSeason(otherSource);
        PlayerSeasonConsolidationProcessor processor = new PlayerSeasonConsolidationProcessor(players, seasons);

        PlayerConsolidationSummary first = processor.consolidate(ImportSource.FCTT);
        PlayerConsolidationSummary second = processor.consolidate(ImportSource.FCTT);

        assertEquals(1, first.playersCreated());
        assertEquals(0, second.playersCreated());
        assertEquals(0, second.registrationsReassociated());
        assertEquals(2, second.alreadyCorrectRegistrations());
        assertTrue(seasons.findPlayerSeasonById(otherSource.getId()).orElseThrow().getFederatedPlayer().isEmpty());
    }

    @Test
    void linksExistingFederatedPlayersToCanonicalPlayersWithoutChangingRegistrations() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        InMemoryRepositories.Players.CanonicalPlayers canonicalPlayers =
                new InMemoryRepositories.Players.CanonicalPlayers();
        InMemoryRepositories.PlayerSeasons seasons = new InMemoryRepositories.PlayerSeasons();
        FederatedPlayer federated = FederatedPlayer.createNew(ImportSource.FCTT, "PLAYER, ONE");
        players.saveFederatedPlayer(federated);
        PlayerSeason registration = save(seasons, "PLAYER, ONE", "1", Season.of(2023), federated);

        PlayerConsolidationSummary summary = new PlayerSeasonConsolidationProcessor(
                players, seasons, canonicalPlayers).consolidate(ImportSource.FCTT);

        assertEquals(1, canonicalPlayers.byId.size());
        assertEquals(canonicalPlayers.byId.values().iterator().next().getId(),
                players.findFederatedPlayerById(federated.getId()).orElseThrow()
                        .getPlayer().orElseThrow().getId());
        assertEquals(registration.getId(), seasons.findPlayerSeasonById(registration.getId()).orElseThrow().getId());
        assertEquals(0, summary.registrationsReassociated());
    }

    @Test
    void skipsConflictingPlayersAndReportModeDoesNotWrite() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        InMemoryRepositories.PlayerSeasons seasons = new InMemoryRepositories.PlayerSeasons();
        FederatedPlayer left = FederatedPlayer.createNew(ImportSource.RFETM, "John Doe");
        FederatedPlayer right = FederatedPlayer.createNew(ImportSource.RFETM, "Different Player");
        players.saveFederatedPlayer(left);
        players.saveFederatedPlayer(right);
        save(seasons, ImportSource.RFETM, "John Doe", "1", Season.of(2023), left);
        save(seasons, ImportSource.RFETM, " john   doe ", "2", Season.of(2024), right);

        PlayerConsolidationSummary conflict = new PlayerSeasonConsolidationProcessor(players, seasons)
                .consolidate(ImportSource.RFETM);
        assertTrue(conflict.warnings().stream().anyMatch(warning -> warning.reason().contains("Conflicting")));

        InMemoryRepositories.Players reportPlayers = new InMemoryRepositories.Players();
        InMemoryRepositories.PlayerSeasons reportSeasons = new InMemoryRepositories.PlayerSeasons();
        save(reportSeasons, ImportSource.RFETM, "John Doe", "1", Season.of(2023), null);
        save(reportSeasons, ImportSource.RFETM, " john   doe ", "2", Season.of(2024), null);
        PlayerConsolidationSummary report = new PlayerSeasonConsolidationProcessor(reportPlayers, reportSeasons)
                .consolidate(ImportSource.RFETM, ConsolidationMode.REPORT);
        assertEquals(1, report.playersCreated());
        assertEquals(0, reportPlayers.byId.size());
        assertTrue(reportSeasons.findAllPlayerSeasonsBySource(ImportSource.RFETM).stream()
                .allMatch(registration -> registration.getFederatedPlayer().isEmpty()));
    }

    @Test
    void rejectsAmbiguousSourceScopedPlayerNameResolution() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        players.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.RFETM, "DUPLICATE PLAYER"));
        players.saveFederatedPlayer(FederatedPlayer.createNew(ImportSource.RFETM, "DUPLICATE PLAYER"));

        assertThrows(IllegalStateException.class, () ->
                players.findFederatedPlayerBySourceAndName(ImportSource.RFETM, "DUPLICATE PLAYER"));
    }

    private static PlayerSeason save(InMemoryRepositories.PlayerSeasons seasons, String name, String license,
                                     Season season, FederatedPlayer player) {
        return save(seasons, ImportSource.FCTT, name, license, season, player);
    }

    private static PlayerSeason save(InMemoryRepositories.PlayerSeasons seasons, ImportSource source, String name,
                                     String license, Season season, FederatedPlayer player) {
        PlayerSeason registration = PlayerSeason.createExisting(UUID.randomUUID(), source, name, license, player, season);
        seasons.savePlayerSeason(registration);
        return registration;
    }
}
