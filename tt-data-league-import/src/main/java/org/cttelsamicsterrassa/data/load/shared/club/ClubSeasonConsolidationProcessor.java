package org.cttelsamicsterrassa.data.load.shared.club;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.ClubSeason;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubSeasonRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Source-scoped historical repair: attach equivalent {@link ClubSeason} registrations to one
 * canonical {@link Club} without merging or deleting season rows.
 */
@Component
public class ClubSeasonConsolidationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClubSeasonConsolidationProcessor.class);

    private static final Set<ImportSource> AUTOMATIC_SOURCES = EnumSet.of(ImportSource.FCTT, ImportSource.BCNESA);

    private final ClubRepository clubRepository;
    private final ClubSeasonRepository clubSeasonRepository;
    private final ClubNameMatcher matcher;

    @Inject
    public ClubSeasonConsolidationProcessor(ClubRepository clubRepository, ClubSeasonRepository clubSeasonRepository) {
        this(clubRepository, clubSeasonRepository, new ClubNameMatcher(new ClubNameNormalizer()));
    }

    ClubSeasonConsolidationProcessor(ClubRepository clubRepository,
                                     ClubSeasonRepository clubSeasonRepository,
                                     ClubNameMatcher matcher) {
        this.clubRepository = Objects.requireNonNull(clubRepository, "clubRepository");
        this.clubSeasonRepository = Objects.requireNonNull(clubSeasonRepository, "clubSeasonRepository");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public ClubConsolidationSummary consolidate(ImportSource source) {
        return consolidate(source, ConsolidationMode.WRITE);
    }

    public ClubConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        if (!AUTOMATIC_SOURCES.contains(source)) {
            LOGGER.info("Skipping club consolidation for {}; automatic matching is disabled", source);
            return ClubConsolidationSummary.disabled(source,
                    "Automatic consolidation is disabled for " + source
                            + " unless a source-specific identity policy is supplied");
        }

        List<ClubSeason> registrations = clubSeasonRepository.findAllClubSeasonsBySource(source);
        ClubConsolidationSummary.Builder summary = ClubConsolidationSummary.builder(source)
                .scannedRegistrations(registrations.size());

        Map<String, List<ClubSeason>> exactGroups = new LinkedHashMap<>();
        for (ClubSeason registration : registrations) {
            if (registration.getName() == null || registration.getName().isBlank()) {
                summary.warning(new ConsolidationWarning(source, "Blank club-season name",
                        List.of(registration.getId()), List.of(String.valueOf(registration.getName()))));
                continue;
            }
            exactGroups.computeIfAbsent(matcher.exactKey(source, registration.getName()), key -> new ArrayList<>())
                    .add(registration);
        }

        int multiMemberExactGroups = 0;
        List<NameGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ClubSeason>> entry : exactGroups.entrySet()) {
            if (entry.getKey().isEmpty()) {
                summary.warning(new ConsolidationWarning(source, "Name collapsed to an empty exact key",
                        ids(entry.getValue()), names(entry.getValue())));
                continue;
            }
            if (entry.getValue().size() > 1) {
                multiMemberExactGroups++;
            }
            groups.add(new NameGroup(entry.getKey(), canonicalDisplayName(entry.getValue()), entry.getValue()));
        }
        summary.exactGroups(multiMemberExactGroups);

        List<FuzzyPair> pairs = acceptMutualFuzzyPairs(source, groups, summary);
        summary.acceptedFuzzyGroups(pairs.size());
        Set<String> paired = pairs.stream()
                .flatMap(pair -> java.util.stream.Stream.of(pair.left().exactKey(), pair.right().exactKey()))
                .collect(Collectors.toSet());

        for (NameGroup group : groups) {
            if (!paired.contains(group.exactKey())) {
                applyCanonicalClub(source, group.members(), group.representativeName(), matchingMode(source, group.members()),
                        mode, summary);
            }
        }
        for (FuzzyPair pair : pairs) {
            List<ClubSeason> combined = concat(pair.left().members(), pair.right().members());
            applyCanonicalClub(source, combined, canonicalDisplayName(combined), MatchingMode.FUZZY, mode, summary);
        }
        ClubConsolidationSummary result = summary.build();
        LOGGER.info("Club consolidation for {}: {}", source, result);
        return result;
    }

    private List<FuzzyPair> acceptMutualFuzzyPairs(ImportSource source,
                                                   List<NameGroup> groups,
                                                   ClubConsolidationSummary.Builder summary) {
        record ScoredPair(NameGroup left, NameGroup right, double score) {
        }
        List<ScoredPair> candidates = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                NameGroup left = groups.get(i);
                NameGroup right = groups.get(j);
                ClubNameComparison comparison = matcher.compare(source, left.representativeName(), right.representativeName());
                if (comparison.fuzzyCandidate()) {
                    candidates.add(new ScoredPair(left, right, comparison.score()));
                } else if (shouldWarn(comparison)) {
                    summary.warning(new ConsolidationWarning(source, warningReason(comparison),
                            ids(concat(left.members(), right.members())),
                            names(concat(left.members(), right.members()))));
                }
            }
        }

        Map<String, List<ScoredPair>> byKey = new LinkedHashMap<>();
        for (ScoredPair candidate : candidates) {
            byKey.computeIfAbsent(candidate.left().exactKey(), key -> new ArrayList<>()).add(candidate);
            byKey.computeIfAbsent(candidate.right().exactKey(), key -> new ArrayList<>()).add(candidate);
        }

        List<FuzzyPair> accepted = new ArrayList<>();
        Set<String> paired = new java.util.HashSet<>();
        for (NameGroup group : groups) {
            if (paired.contains(group.exactKey())) {
                continue;
            }
            List<ScoredPair> options = byKey.getOrDefault(group.exactKey(), List.of());
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
            ScoredPair bestPair = bestOptions.getFirst();
            NameGroup other = bestPair.left().exactKey().equals(group.exactKey()) ? bestPair.right() : bestPair.left();
            List<ScoredPair> otherOptions = byKey.getOrDefault(other.exactKey(), List.of());
            double otherBest = otherOptions.stream().mapToDouble(ScoredPair::score).max().orElse(0);
            List<ScoredPair> otherBestOptions = otherOptions.stream().filter(option -> option.score() == otherBest).toList();
            boolean mutual = otherBestOptions.size() == 1
                    && (otherBestOptions.getFirst().left().exactKey().equals(group.exactKey())
                    || otherBestOptions.getFirst().right().exactKey().equals(group.exactKey()));
            if (!mutual) {
                summary.warning(new ConsolidationWarning(source, "Fuzzy match is not a mutual best match",
                        ids(concat(group.members(), other.members())),
                        names(concat(group.members(), other.members()))));
                continue;
            }
            if (hasConflictingClubs(concat(group.members(), other.members()))) {
                summary.warning(new ConsolidationWarning(source, "Conflicting associated clubs",
                        ids(concat(group.members(), other.members())),
                        names(concat(group.members(), other.members()))));
                continue;
            }
            accepted.add(new FuzzyPair(group, other));
            paired.add(group.exactKey());
            paired.add(other.exactKey());
        }
        return accepted;
    }

    private void applyCanonicalClub(ImportSource source,
                                    List<ClubSeason> members,
                                    String representativeName,
                                    MatchingMode matchingMode,
                                    ConsolidationMode mode,
                                    ClubConsolidationSummary.Builder summary) {
        if (hasConflictingClubs(members)) {
            summary.warning(new ConsolidationWarning(source, "Conflicting associated clubs", ids(members), names(members)));
            return;
        }

        Optional<Club> agreed = uniqueAssociatedClub(members);
        Club canonical;
        boolean created = false;
        if (agreed.isPresent()) {
            canonical = agreed.get();
            if (!canonical.getName().equals(representativeName)) {
                if (mode == ConsolidationMode.WRITE) {
                    canonical.modifyName(representativeName);
                    clubRepository.saveClub(canonical);
                }
                LOGGER.info("Canonicalized club {} name from {} to {}", canonical.getId(), canonical.getName(), representativeName);
            }
        } else {
            String clubName = representativeName;
            Optional<Club> existing = clubRepository.findClubBySourceAndName(source, clubName);
            if (existing.isPresent()) {
                canonical = existing.get();
            } else if (mode == ConsolidationMode.WRITE) {
                canonical = Club.createNew(source, clubName);
                clubRepository.saveClub(canonical);
                created = true;
                summary.incrementClubsCreated();
                LOGGER.info("Created canonical club {} ({}) for {}", canonical.getId(), clubName, source);
            } else {
                canonical = Club.createNew(source, clubName);
                created = true;
                summary.incrementClubsCreated();
            }
        }

        int reassociatedHere = 0;
        for (ClubSeason member : members) {
            if (member.getClub().map(club -> club.getId().equals(canonical.getId())).orElse(false)) {
                summary.incrementAlreadyCorrect();
                continue;
            }
            if (mode == ConsolidationMode.WRITE) {
                ClubSeason updated = member.withClub(canonical);
                clubSeasonRepository.saveClubSeason(updated);
            }
            summary.incrementReassociated();
            reassociatedHere++;
            LOGGER.info("Reassociated club-season {} ({}) to club {} ({})",
                    member.getId(), member.getName(), canonical.getId(), canonical.getName());
        }

        if (created || reassociatedHere > 0) {
            summary.consolidation(new ConsolidatedClub(
                    source, canonical.getName(), canonical.getId(), matchingMode, ids(members), names(members)));
        }
    }

    private static boolean shouldWarn(ClubNameComparison comparison) {
        return switch (comparison.classification()) {
            case REJECTED_SHORT, REJECTED_BELOW_THRESHOLD -> true;
            case REJECTED_TOKEN_MISMATCH -> comparison.leftTokens().stream().anyMatch(comparison.rightTokens()::contains);
            default -> false;
        };
    }

    private static String warningReason(ClubNameComparison comparison) {
        return switch (comparison.classification()) {
            case REJECTED_SHORT -> "Rejected short or one-token club name";
            case REJECTED_BELOW_THRESHOLD -> "Fuzzy score below threshold";
            case REJECTED_TOKEN_MISMATCH -> "Different significant tokens";
            default -> comparison.classification().name();
        };
    }

    private static boolean hasConflictingClubs(List<ClubSeason> members) {
        return associatedClubIds(members).size() > 1;
    }

    private static Optional<Club> uniqueAssociatedClub(List<ClubSeason> members) {
        List<Club> clubs = members.stream()
                .map(ClubSeason::getClub)
                .flatMap(Optional::stream)
                .toList();
        if (clubs.isEmpty()) {
            return Optional.empty();
        }
        UUID first = clubs.getFirst().getId();
        if (clubs.stream().allMatch(club -> first.equals(club.getId()))) {
            return Optional.of(selectAssociatedClub(members));
        }
        return Optional.empty();
    }

    private static Club selectAssociatedClub(List<ClubSeason> members) {
        return members.stream()
                .filter(member -> member.getClub().isPresent())
                .sorted(registrationOrder())
                .map(member -> member.getClub().orElseThrow())
                .findFirst()
                .orElseThrow();
    }

    private static String representativeName(List<ClubSeason> members) {
        return members.stream()
                .sorted(registrationOrder())
                .map(ClubSeason::getName)
                .findFirst()
                .orElseThrow();
    }

    private String canonicalDisplayName(List<ClubSeason> members) {
        return matcher.preferredDisplayName(members.getFirst().getSource(), names(members));
    }

    private MatchingMode matchingMode(ImportSource source, List<ClubSeason> members) {
        return members.stream()
                .map(ClubSeason::getName)
                .map(name -> matcher.parts(source, name).appliedRules())
                .anyMatch(rules -> !rules.isEmpty()) ? MatchingMode.RULED_VARIANT : MatchingMode.EXACT;
    }

    private static Comparator<ClubSeason> registrationOrder() {
        return Comparator.comparing((ClubSeason member) -> Optional.ofNullable(member.getSeason())
                        .map(Season::toString)
                        .orElse(""))
                .thenComparing(member -> Optional.ofNullable(member.getName()).orElse(""))
                .thenComparing(ClubSeason::getId);
    }

    private static Set<UUID> associatedClubIds(List<ClubSeason> members) {
        return members.stream()
                .map(ClubSeason::getClub)
                .flatMap(Optional::stream)
                .map(Club::getId)
                .collect(Collectors.toSet());
    }

    private static List<UUID> ids(List<ClubSeason> members) {
        return members.stream().map(ClubSeason::getId).toList();
    }

    private static List<String> names(List<ClubSeason> members) {
        return members.stream().map(ClubSeason::getName).toList();
    }

    private static List<ClubSeason> concat(List<ClubSeason> left, List<ClubSeason> right) {
        List<ClubSeason> combined = new ArrayList<>(left.size() + right.size());
        combined.addAll(left);
        combined.addAll(right);
        return combined;
    }

    private record NameGroup(String exactKey, String representativeName, List<ClubSeason> members) {
    }

    private record FuzzyPair(NameGroup left, NameGroup right) {
    }
}
