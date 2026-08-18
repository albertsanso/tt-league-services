package org.cttelsamicsterrassa.data.load.shared.player;

import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.club.ConsolidationMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSeasonConsolidationProcessorTest {

    @Test
    void representsAnUnassignedPlayerSeasonWithAnEmptyOptional() {
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "PLAYER", "1", null, Season.of(2023));

        assertTrue(registration.getPlayer().isEmpty());
        assertSame(registration, registration.withPlayer(null));
    }

    @Test
    void preservesRegistrationWhenReplacingOrReusingPlayerAssociation() {
        Player first = Player.createNew(ImportSource.FCTT, "PLAYER");
        Player second = Player.createNew(ImportSource.FCTT, "PLAYER");
        PlayerSeason registration = PlayerSeason.createExisting(
                UUID.randomUUID(), ImportSource.FCTT, "PLAYER", "1", first, Season.of(2023));

        assertSame(registration, registration.withPlayer(first));
        PlayerSeason replaced = registration.withPlayer(second);

        assertEquals(second.getId(), replaced.getPlayer().orElseThrow().getId());
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
        assertEquals(seasons.findPlayerSeasonById(first.getId()).orElseThrow().getPlayer().orElseThrow().getId(),
                seasons.findPlayerSeasonById(second.getId()).orElseThrow().getPlayer().orElseThrow().getId());
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
        assertTrue(seasons.findPlayerSeasonById(otherSource.getId()).orElseThrow().getPlayer().isEmpty());
    }

    @Test
    void skipsConflictingPlayersAndReportModeDoesNotWrite() {
        InMemoryRepositories.Players players = new InMemoryRepositories.Players();
        InMemoryRepositories.PlayerSeasons seasons = new InMemoryRepositories.PlayerSeasons();
        Player left = Player.createNew(ImportSource.RFETM, "John Doe");
        Player right = Player.createNew(ImportSource.RFETM, "Different Player");
        players.savePlayer(left);
        players.savePlayer(right);
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
                .allMatch(registration -> registration.getPlayer().isEmpty()));
    }

    private static PlayerSeason save(InMemoryRepositories.PlayerSeasons seasons, String name, String license,
                                     Season season, Player player) {
        return save(seasons, ImportSource.FCTT, name, license, season, player);
    }

    private static PlayerSeason save(InMemoryRepositories.PlayerSeasons seasons, ImportSource source, String name,
                                     String license, Season season, Player player) {
        PlayerSeason registration = PlayerSeason.createExisting(UUID.randomUUID(), source, name, license, player, season);
        seasons.savePlayerSeason(registration);
        return registration;
    }
}
