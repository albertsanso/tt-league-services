package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlayerSeasonConsolidationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSeasonConsolidationProcessor.class);

    private final FederatedPlayerRepository federatedPlayerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final PlayerRepository canonicalPlayerRepository;

    @Autowired
    public PlayerSeasonConsolidationProcessor(FederatedPlayerRepository federatedPlayerRepository,
                                              PlayerSeasonRepository playerSeasonRepository,
                                              PlayerRepository canonicalPlayerRepository) {
        this.federatedPlayerRepository = federatedPlayerRepository;
        this.playerSeasonRepository = playerSeasonRepository;
        this.canonicalPlayerRepository = canonicalPlayerRepository;
    }

    public PlayerConsolidationSummary consolidate(ImportSource source) {
        return consolidate(source, ConsolidationMode.WRITE);
    }

    public PlayerConsolidationSummary consolidate2(ImportSource source) {
        List<PlayerSeason> playerSeasons = playerSeasonRepository.findAllPlayerSeasonsBySource(source);
        Map<PlayerIdentity, List<PlayerSeason>> groups = new LinkedHashMap<>();
        List<ConsolidationWarning> warnings = new ArrayList<>();
        List<ConsolidationWarning> errors = new ArrayList<>();

        for (PlayerSeason playerSeason : playerSeasons) {
            String licenseId = playerSeason.getLicenseId() == null ? "" : playerSeason.getLicenseId().trim();
            if (licenseId.isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank licenseId for " + playerSeason.getId()));
                continue;
            }
            groups.computeIfAbsent(new PlayerIdentity(source, licenseId), ignored -> new ArrayList<>()).add(playerSeason);
        }

        return null;
    }
    public PlayerConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
        List<PlayerSeason> seasons = playerSeasonRepository.findAllPlayerSeasonsBySource(source).stream()
                .sorted(Comparator.comparing(PlayerSeason::getLicenseId, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(playerSeason -> playerSeason.getId().toString()))
                .toList();

        Map<PlayerIdentity, List<PlayerSeason>> groups = new LinkedHashMap<>();
        List<ConsolidationWarning> warnings = new ArrayList<>();
        List<ConsolidationWarning> errors = new ArrayList<>();
        for (PlayerSeason playerSeason : seasons) {
            String licenseId = playerSeason.getLicenseId() == null ? "" : playerSeason.getLicenseId().trim();
            if (licenseId.isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank licenseId for " + playerSeason.getId()));
                continue;
            }
            groups.computeIfAbsent(new PlayerIdentity(source, licenseId), ignored -> new ArrayList<>()).add(playerSeason);
        }

        int exactGroups = (int) groups.values().stream().filter(group -> group.size() > 1).count();
        int playersCreated = 0;
        int canonicalLinksCreated = 0;
        int reassociated = 0;
        int alreadyCorrect = 0;

        for (List<PlayerSeason> group : groups.values()) {
            List<PlayerSeason> members = group.stream()
                    .sorted(Comparator.comparing((PlayerSeason playerSeason) -> playerSeason.getSeason().start())
                            .thenComparing(playerSeason -> playerSeason.getId().toString()))
                    .toList();
            String licenseId = members.get(0).getLicenseId().trim();
            String canonicalDisplayName = preferredDisplayName(members.stream().map(PlayerSeason::getName).toList());
            Set<FederatedPlayer> existing = members.stream()
                    .map(playerSeason -> playerSeason.getFederatedPlayer().orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (existing.size() > 1) {
                warnings.add(new ConsolidationWarning("Conflicting existing federated players for " + canonicalDisplayName));
                //continue;
            }

            FederatedPlayer target = existing.stream().findFirst().orElse(null);
            if (target == null) {
                target = federatedPlayerRepository
                        .findFederatedPlayerBySourceAndLicenseId(source, licenseId)
                        .orElse(null);
            }
            if (target == null) {
                playersCreated++;
                if (mode == ConsolidationMode.WRITE) {
                    target = FederatedPlayer.createNew(
                            source, canonicalDisplayName, licenseId);
                    federatedPlayerRepository.saveFederatedPlayer(target);
                }
            }

            if (target != null && mode == ConsolidationMode.WRITE && !target.getName().equals(canonicalDisplayName)) {
                target.modifyName(canonicalDisplayName);
                federatedPlayerRepository.saveFederatedPlayer(target);
            }
            if (target != null && mode == ConsolidationMode.WRITE
                    && target.getLicenseId() == null) {
                target = target.withLicenseId(licenseId);
                federatedPlayerRepository.saveFederatedPlayer(target);
            }

            /* Canonical assignation to the Federated Player if it doesn't have one yet, or Player creation */
            if (target != null && canonicalPlayerRepository != null && target.getPlayer().isEmpty()) {
                Player canonical = mode == ConsolidationMode.WRITE
                        ? createAndStoreCanonicalPlayer(canonicalDisplayName, licenseId)
                        : Player.createExisting(UUID.randomUUID(), canonicalDisplayName, licenseId);
                if (mode == ConsolidationMode.WRITE) {
                    federatedPlayerRepository.saveFederatedPlayer(target.withPlayer(canonical));
                }
                canonicalLinksCreated++;
            }

            for (PlayerSeason member : members) {
                UUID currentId = member.getFederatedPlayer().map(FederatedPlayer::getId).orElse(null);
                UUID targetId = target != null ? target.getId() : null;
                if (Objects.equals(currentId, targetId)) {
                    alreadyCorrect++;
                    continue;
                }
                reassociated++;
                if (mode == ConsolidationMode.WRITE) {
                    playerSeasonRepository.savePlayerSeason(member.withFederatedPlayer(target));
                }
            }
        }

        PlayerConsolidationSummary summary = new PlayerConsolidationSummary(
                source,
                mode,
                seasons.size(),
                exactGroups,
                playersCreated,
                canonicalLinksCreated,
                reassociated,
                alreadyCorrect,
                List.copyOf(warnings),
                List.copyOf(errors));
        LOGGER.info("Player consolidation finished for {} in {} mode: {}", source, mode, summary);
        return summary;
    }

    private Player createAndStoreCanonicalPlayer(String canonicalDisplayName, String licenseId) {
        Player created = Player.createNew(canonicalDisplayName, licenseId);
        canonicalPlayerRepository.savePlayer(created);
        return created;
    }

    private static String preferredDisplayName(List<String> names) {
        return names.stream()
                .map(PlayerSeasonConsolidationProcessor::normalizeDisplay)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted()
                .findFirst()
                .orElse("");
    }

    private static String normalizeDisplay(String name) {
        if (name == null) {
            return "";
        }
        String candidate = name.trim().replaceAll("\\s+", " ");
        if (candidate.contains(",")) {
            String[] parts = candidate.split(",", 2);
            if (parts.length == 2) {
                candidate = parts[1].trim() + " " + parts[0].trim();
            }
        }
        return candidate.replaceAll("\\s+", " ");
    }

    private record PlayerIdentity(ImportSource source, String licenseId) {
    }
}
