package org.cttelsamicsterrassa.data.load.shared.player.consolidate;

import org.cttelsamicsterrassa.data.core.domain.player.model.FederatedPlayer;
import org.cttelsamicsterrassa.data.core.domain.player.model.Player;
import org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason;
import org.cttelsamicsterrassa.data.core.domain.player.repository.FederatedPlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerRepository;
import org.cttelsamicsterrassa.data.core.domain.player.repository.PlayerSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.player.CanonicalPlayerResolver;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlayerSeasonConsolidationProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerSeasonConsolidationProcessor.class);

    private final FederatedPlayerRepository playerRepository;
    private final PlayerSeasonRepository playerSeasonRepository;
    private final PlayerNameMatcher matcher;
    private final CanonicalPlayerResolver canonicalPlayerResolver;

    public PlayerSeasonConsolidationProcessor(FederatedPlayerRepository playerRepository,
                                              PlayerSeasonRepository playerSeasonRepository) {
        this(playerRepository, playerSeasonRepository, new PlayerNameMatcher(new PlayerNameNormalizer()), null);
    }

    @Inject
    public PlayerSeasonConsolidationProcessor(FederatedPlayerRepository playerRepository,
                                              PlayerSeasonRepository playerSeasonRepository,
                                              PlayerRepository canonicalPlayerRepository) {
        this(playerRepository, playerSeasonRepository, new PlayerNameMatcher(new PlayerNameNormalizer()),
                new CanonicalPlayerResolver(canonicalPlayerRepository));
    }

    PlayerSeasonConsolidationProcessor(FederatedPlayerRepository playerRepository,
                                       PlayerSeasonRepository playerSeasonRepository,
                                       PlayerNameMatcher matcher) {
        this(playerRepository, playerSeasonRepository, matcher, null);
    }

    private PlayerSeasonConsolidationProcessor(FederatedPlayerRepository playerRepository,
                                               PlayerSeasonRepository playerSeasonRepository,
                                               PlayerNameMatcher matcher,
                                               CanonicalPlayerResolver canonicalPlayerResolver) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.playerSeasonRepository = Objects.requireNonNull(playerSeasonRepository, "playerSeasonRepository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.canonicalPlayerResolver = canonicalPlayerResolver;
    }

    public PlayerConsolidationSummary consolidate(ImportSource source) {
        return consolidate(source, ConsolidationMode.WRITE);
    }

    public PlayerConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        List<PlayerSeason> registrations = playerSeasonRepository.findAllPlayerSeasonsBySource(source);
        PlayerConsolidationSummary.Builder summary = PlayerConsolidationSummary.builder(source)
                .scannedRegistrations(registrations.size());
        Map<String, List<PlayerSeason>> exactGroups = new LinkedHashMap<>();
        for (PlayerSeason registration : registrations) {
            if (registration.getName() == null || registration.getName().isBlank()) {
                summary.warning(new ConsolidationWarning(source, "Blank player-season name",
                        List.of(registration.getId()), List.of(registration.getName())));
                continue;
            }
            String key = matcher.exactKey(registration.getName());
            if (key.isEmpty()) {
                summary.warning(new ConsolidationWarning(source, "Name collapsed to an empty exact key",
                        List.of(registration.getId()), List.of(registration.getName())));
                continue;
            }
            exactGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(registration);
        }
        int duplicateGroups = (int) exactGroups.values().stream().filter(group -> group.size() > 1).count();
        summary.exactGroups(duplicateGroups);
        List<NameGroup> groups = exactGroups.entrySet().stream()
                .map(entry -> new NameGroup(entry.getKey(), entry.getValue()))
                .toList();

        List<FuzzyPair> fuzzyPairs = findFuzzyPairs(groups, source, summary);
        summary.acceptedFuzzyGroups(fuzzyPairs.size());
        Set<String> paired = fuzzyPairs.stream()
                .flatMap(pair -> java.util.stream.Stream.of(pair.left().key(), pair.right().key()))
                .collect(Collectors.toSet());
        for (NameGroup group : groups) {
            if (!paired.contains(group.key())) {
                applyCanonical(source, group.members(), MatchingMode.EXACT, mode, summary);
            }
        }
        for (FuzzyPair pair : fuzzyPairs) {
            List<PlayerSeason> members = new ArrayList<>(pair.left().members());
            members.addAll(pair.right().members());
            applyCanonical(source, members, MatchingMode.FUZZY, mode, summary);
        }
        PlayerConsolidationSummary result = summary.build();
        LOGGER.info("FederatedPlayer consolidation for {}: {}", source, result);
        return result;
    }

    private List<FuzzyPair> findFuzzyPairs(List<NameGroup> groups, ImportSource source,
                                           PlayerConsolidationSummary.Builder summary) {
        record ScoredPair(NameGroup left, NameGroup right, double score) {}
        List<ScoredPair> candidates = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                NameGroup left = groups.get(i);
                NameGroup right = groups.get(j);
                PlayerNameComparison comparison = matcher.compare(left.representativeName(), right.representativeName());
                if (comparison.fuzzyCandidate()) {
                    candidates.add(new ScoredPair(left, right, comparison.score()));
                } else if (comparison.classification() != PlayerNameMatchClass.EXACT) {
                    summary.warning(new ConsolidationWarning(source, warningReason(comparison),
                            ids(concat(left.members(), right.members())), names(concat(left.members(), right.members()))));
                }
            }
        }
        Map<String, List<ScoredPair>> byKey = new LinkedHashMap<>();
        for (ScoredPair candidate : candidates) {
            byKey.computeIfAbsent(candidate.left().key(), ignored -> new ArrayList<>()).add(candidate);
            byKey.computeIfAbsent(candidate.right().key(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<FuzzyPair> accepted = new ArrayList<>();
        Set<String> paired = new HashSet<>();
        for (NameGroup group : groups) {
            if (paired.contains(group.key())) {
                continue;
            }
            List<ScoredPair> options = byKey.getOrDefault(group.key(), List.of());
            if (options.isEmpty()) {
                continue;
            }
            double best = options.stream().mapToDouble(ScoredPair::score).max().orElse(0);
            List<ScoredPair> bestOptions = options.stream().filter(option -> option.score() == best).toList();
            if (bestOptions.size() != 1) {
                summary.warning(new ConsolidationWarning(source, "Ambiguous fuzzy match",
                        ids(group.members()), names(group.members())));
                continue;
            }
            ScoredPair selected = bestOptions.getFirst();
            NameGroup other = selected.left().key().equals(group.key()) ? selected.right() : selected.left();
            List<ScoredPair> otherOptions = byKey.getOrDefault(other.key(), List.of());
            double otherBest = otherOptions.stream().mapToDouble(ScoredPair::score).max().orElse(0);
            List<ScoredPair> mutualOptions = otherOptions.stream()
                    .filter(option -> option.score() == otherBest)
                    .filter(option -> option.left().key().equals(group.key()) || option.right().key().equals(group.key()))
                    .toList();
            if (mutualOptions.size() != 1) {
                summary.warning(new ConsolidationWarning(source, "Fuzzy match is not a mutual best match",
                        ids(concat(group.members(), other.members())), names(concat(group.members(), other.members()))));
                continue;
            }
            if (hasConflictingPlayers(concat(group.members(), other.members()))) {
                summary.warning(new ConsolidationWarning(source, "Conflicting associated players",
                        ids(concat(group.members(), other.members())), names(concat(group.members(), other.members()))));
                continue;
            }
            accepted.add(new FuzzyPair(group, other));
            paired.add(group.key());
            paired.add(other.key());
        }
        return accepted;
    }

    private void applyCanonical(ImportSource source, List<PlayerSeason> members, MatchingMode matchingMode,
                                ConsolidationMode mode, PlayerConsolidationSummary.Builder summary) {
        if (hasConflictingPlayers(members)) {
            summary.warning(new ConsolidationWarning(source, "Conflicting associated players", ids(members), names(members)));
            return;
        }
        String displayName = matcher.preferredDisplayName(names(members));
        FederatedPlayer canonical = uniquePlayer(members).orElseGet(() ->
                playerRepository.findFederatedPlayerBySourceAndName(source, displayName).orElse(null));
        boolean created = false;
        if (canonical == null) {
            canonical = FederatedPlayer.createNew(source, displayName);
            created = true;
            summary.incrementPlayersCreated();
            if (mode == ConsolidationMode.WRITE) {
                playerRepository.saveFederatedPlayer(canonical);
            }
        }
        canonical = linkCanonicalPlayer(canonical, displayName, mode);

        UUID canonicalId = canonical.getId();
        for (PlayerSeason member : members) {
            if (member.getFederatedPlayer().map(player -> player.getId().equals(canonicalId)).orElse(false)) {
                summary.incrementAlreadyCorrect();
                continue;
            }

            if (mode == ConsolidationMode.WRITE) {
                playerSeasonRepository.savePlayerSeason(member.withFederatedPlayer(canonical));
            }
            summary.incrementReassociated();
        }
        if (created || members.stream().anyMatch(member -> member.getFederatedPlayer()
                .map(player -> !player.getId().equals(canonicalId)).orElse(true))) {
            summary.consolidation(new ConsolidatedFederatedPlayer(source, canonical.getName(), canonical.getId(),
                    matchingMode, ids(members), names(members)));
        }
    }

    private FederatedPlayer linkCanonicalPlayer(FederatedPlayer federatedPlayer,
                                                String canonicalName,
                                                ConsolidationMode mode) {
        if (canonicalPlayerResolver == null || federatedPlayer.getPlayer().isPresent()) {
            return federatedPlayer;
        }
        Player player = mode == ConsolidationMode.WRITE
                ? canonicalPlayerResolver.resolveOrCreate(canonicalName)
                : canonicalPlayerResolver.findOrCreateForReport(canonicalName);
        FederatedPlayer linked = federatedPlayer.withPlayer(player);
        if (mode == ConsolidationMode.WRITE) {
            playerRepository.saveFederatedPlayer(linked);
        }
        return linked;
    }

    private static Optional<FederatedPlayer> uniquePlayer(List<PlayerSeason> members) {
        List<FederatedPlayer> players = members.stream().map(PlayerSeason::getFederatedPlayer).flatMap(Optional::stream).toList();
        return players.stream().map(FederatedPlayer::getId).distinct().count() == 1 ? Optional.of(players.getFirst()) : Optional.empty();
    }

    private static boolean hasConflictingPlayers(List<PlayerSeason> members) {
        return members.stream().map(PlayerSeason::getFederatedPlayer).flatMap(Optional::stream)
                .map(FederatedPlayer::getId).distinct().count() > 1;
    }

    private static Comparator<PlayerSeason> registrationOrder() {
        return Comparator.comparing((PlayerSeason registration) -> Optional.ofNullable(registration.getSeason())
                        .map(Object::toString).orElse(""))
                .thenComparing(registration -> Optional.ofNullable(registration.getName()).orElse(""))
                .thenComparing(PlayerSeason::getId);
    }

    private static String warningReason(PlayerNameComparison comparison) {
        return switch (comparison.classification()) {
            case REJECTED_SHORT -> "Rejected short or one-token player name";
            case REJECTED_BELOW_THRESHOLD -> "Fuzzy score below threshold";
            case REJECTED_TOKEN_MISMATCH -> "Different significant tokens";
            default -> comparison.classification().name();
        };
    }

    private static List<PlayerSeason> concat(List<PlayerSeason> left, List<PlayerSeason> right) {
        List<PlayerSeason> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    private static List<UUID> ids(List<PlayerSeason> members) { return members.stream().map(PlayerSeason::getId).toList(); }
    private static List<String> names(List<PlayerSeason> members) { return members.stream().map(PlayerSeason::getName).toList(); }

    private record NameGroup(String key, List<PlayerSeason> members) {
        String representativeName() {
            return members.stream().sorted(registrationOrder()).map(PlayerSeason::getName).findFirst().orElseThrow();
        }
    }
    private record FuzzyPair(NameGroup left, NameGroup right) {}
}
