package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves source-scoped federated clubs to season-independent canonical clubs.
 */
@Component
public class FederatedClubToCanonicalClubConsolidationProcessor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FederatedClubToCanonicalClubConsolidationProcessor.class);

    private final FederatedClubRepository federatedClubRepository;
    private final ClubRepository clubRepository;
    private final CanonicalClubResolver canonicalClubResolver;
    private final ClubNameNormalizer normalizer;
    private final ClubNameMatcher matcher;
    private final FederatedClubClusterLabeler clusterLabeler;

    @Inject
    public FederatedClubToCanonicalClubConsolidationProcessor(
            FederatedClubRepository federatedClubRepository,
            ClubRepository clubRepository) {
        this(federatedClubRepository, clubRepository, new ClubNameNormalizer());
    }

    FederatedClubToCanonicalClubConsolidationProcessor(
            FederatedClubRepository federatedClubRepository,
            ClubRepository clubRepository,
            ClubNameNormalizer normalizer) {
        this.federatedClubRepository = Objects.requireNonNull(federatedClubRepository);
        this.clubRepository = Objects.requireNonNull(clubRepository);
        this.canonicalClubResolver = new CanonicalClubResolver(this.clubRepository);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.matcher = new ClubNameMatcher(normalizer);
        this.clusterLabeler = new FederatedClubClusterLabeler(normalizer);
    }

    public ClubConsolidationSummary consolidate(ImportSource source) {
        return consolidate(source, ConsolidationMode.WRITE);
    }

    public ClubConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        LOGGER.info("Starting federated-club to canonical-club consolidation for {} in {} mode", source, mode);

        List<ConsolidationWarning> warnings = new ArrayList<>();
        List<ConsolidationWarning> errors = new ArrayList<>();
        List<FederatedClub> inventory = federatedClubRepository.findAllFederatedClubsBySource(source).stream()
                .sorted(Comparator.comparing(FederatedClub::getName, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(club -> club.getId().toString()))
                .toList();
        Map<String, List<FederatedClub>> exactGroups = new LinkedHashMap<>();
        for (FederatedClub club : inventory) {
            if (club.getName() == null || club.getName().isBlank()) {
                warnings.add(new ConsolidationWarning("Unlinkable federated club with blank name: " + club.getId()));
                continue;
            }
            String key = normalizer.exactKey(source, club.getName());
            if (key.isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank normalized key for federated club " + club.getId()));
                continue;
            }
            exactGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(club);
        }

        List<Group> groups = exactGroups.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue(),
                        normalizer.preferredDisplayName(source,
                                entry.getValue().stream().map(FederatedClub::getName).toList()),
                        "exact-key", 1.0d))
                .sorted(Comparator.comparing(Group::key).thenComparing(Group::displayName))
                .toList();
        int exactGroupsCount = (int) groups.stream().filter(group -> group.members().size() > 1).count();
        FuzzyResult fuzzyResult = mergeSimilarityClusters(source, groups, warnings);

        int clubsCreated = 0;
        int canonicalLinksCreated = 0;
        int alreadyCorrect = 0;
        List<ConsolidatedClub> consolidations = new ArrayList<>();
        for (Group group : fuzzyResult.groups()) {
            List<FederatedClub> members = group.members().stream()
                    .sorted(Comparator.comparing(FederatedClub::getName).thenComparing(club -> club.getId().toString()))
                    .toList();
            if (members.stream().map(FederatedClub::getName).distinct().count() < members.size()) {
                warnings.add(new ConsolidationWarning("Duplicate source/name federated clubs for key "
                        + group.key()));
            }
            if (group.displayName().isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank canonical name for key " + group.key()));
                continue;
            }

            Set<UUID> existingIds = members.stream()
                    .flatMap(club -> club.getClub().stream())
                    .map(Club::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (existingIds.size() > 1) {
                warnings.add(new ConsolidationWarning("Conflicting canonical links for federated-club key "
                        + group.key()));
                continue;
            }

            Club canonical;
            if (existingIds.size() == 1) {
                canonical = members.stream().flatMap(club -> club.getClub().stream()).findFirst().orElseThrow();
            } else {
                boolean canonicalExists = canonicalClubResolver.findExisting(source, group.displayName()).isPresent();
                canonical = mode == ConsolidationMode.WRITE
                        ? canonicalClubResolver.resolveOrCreate(source, group.displayName())
                        : canonicalClubResolver.findOrCreateForReport(source, group.displayName());
                if (!canonicalExists) {
                    clubsCreated++;
                }
            }

            for (FederatedClub member : members) {
                if (member.getClub().map(existing -> existing.getId().equals(canonical.getId())).orElse(false)) {
                    alreadyCorrect++;
                    continue;
                }
                canonicalLinksCreated++;
                if (mode == ConsolidationMode.WRITE) {
                    federatedClubRepository.saveFederatedClub(member.withClub(canonical));
                }
            }
            consolidations.add(new ConsolidatedClub(source, group.key(), group.displayName(),
                    group.matchRule(), group.confidence(), members.stream().map(FederatedClub::getId).toList()));
        }

        ClubConsolidationSummary summary = new ClubConsolidationSummary(
                source, mode, inventory.size(), exactGroupsCount, fuzzyResult.acceptedFuzzyGroups(),
                clubsCreated, canonicalLinksCreated, 0, alreadyCorrect,
                List.copyOf(consolidations), List.copyOf(warnings), List.copyOf(errors));
        LOGGER.info("Federated-club to canonical-club consolidation finished for {}: {}", source, summary);
        return summary;
    }

    private FuzzyResult mergeSimilarityClusters(ImportSource source, List<Group> groups,
                                                 List<ConsolidationWarning> warnings) {
        int[] parents = new int[groups.size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
        }
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                Group left = groups.get(i);
                Group right = groups.get(j);
                ClubNameComparison comparison = matcher.compare(source, left.displayName(), right.displayName());
                if (comparison.fuzzyCandidate()) {
                    union(parents, i, j);
                } else if (!comparison.exact()) {
                    warnings.add(new ConsolidationWarning("Rejected federated-club candidate: "
                            + left.displayName() + " <-> " + right.displayName()));
                }
            }
        }

        Map<Integer, List<Group>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            clusters.computeIfAbsent(find(parents, i), ignored -> new ArrayList<>()).add(groups.get(i));
        }
        List<Group> merged = new ArrayList<>();
        int accepted = 0;
        for (List<Group> cluster : clusters.values()) {
            List<FederatedClub> members = cluster.stream()
                    .flatMap(group -> group.members().stream())
                    .toList();
            boolean fuzzy = cluster.size() > 1;
            if (fuzzy) {
                accepted++;
            }
            Group first = cluster.getFirst();
            double confidence = cluster.stream()
                    .flatMap(left -> cluster.stream()
                            .filter(right -> cluster.indexOf(left) < cluster.indexOf(right))
                            .map(right -> matcher.compare(source, left.displayName(), right.displayName())))
                    .filter(ClubNameComparison::fuzzyCandidate)
                    .mapToDouble(ClubNameComparison::score)
                    .min()
                    .orElse(first.confidence());
            merged.add(new Group(
                    cluster.stream().map(Group::key).min(String::compareTo).orElse(first.key()),
                    List.copyOf(members),
                    clusterLabeler.label(source, members),
                    fuzzy ? "similarity-cluster" : first.matchRule(),
                    confidence));
        }
        merged.sort(Comparator.comparing(Group::key).thenComparing(Group::displayName));
        return new FuzzyResult(List.copyOf(merged), accepted);
    }

    private static void union(int[] parents, int left, int right) {
        int leftRoot = find(parents, left);
        int rightRoot = find(parents, right);
        if (leftRoot != rightRoot) {
            parents[rightRoot] = leftRoot;
        }
    }

    private static int find(int[] parents, int node) {
        if (parents[node] != node) {
            parents[node] = find(parents, parents[node]);
        }
        return parents[node];
    }

    private record Group(String key, List<FederatedClub> members, String displayName,
                         String matchRule, double confidence) {
    }

    private record FuzzyResult(List<Group> groups, int acceptedFuzzyGroups) {
    }
}
