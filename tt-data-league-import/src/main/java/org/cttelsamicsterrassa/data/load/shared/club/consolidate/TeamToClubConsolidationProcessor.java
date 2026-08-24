package org.cttelsamicsterrassa.data.load.shared.club.consolidate;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
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
public class TeamToClubConsolidationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamToClubConsolidationProcessor.class);

    private final FederatedClubRepository federatedClubRepository;
    private final TeamRepository teamRepository;
    private final CanonicalClubResolver canonicalClubResolver;
    private final ClubNameNormalizer normalizer;
    private final ClubNameMatcher matcher;
    private final CommonTermClubResolver commonTermClubResolver;

    public TeamToClubConsolidationProcessor(FederatedClubRepository federatedClubRepository,
                                            TeamRepository teamRepository,
                                            ClubRepository clubRepository) {
        this(federatedClubRepository, teamRepository, clubRepository,
                new ClubNameNormalizer(), new CommonTermClubResolver());
    }

    @Autowired
    private TeamToClubConsolidationProcessor(FederatedClubRepository federatedClubRepository,
                                             TeamRepository teamRepository,
                                             ClubRepository clubRepository,
                                             ClubNameNormalizer normalizer,
                                             CommonTermClubResolver commonTermClubResolver) {
        this.federatedClubRepository = federatedClubRepository;
        this.teamRepository = teamRepository;
        this.canonicalClubResolver = new CanonicalClubResolver(clubRepository);
        this.normalizer = normalizer;
        this.matcher = new ClubNameMatcher(normalizer);
        this.commonTermClubResolver = commonTermClubResolver;
    }

    public ClubConsolidationSummary consolidate(ImportSource source) {
        return consolidate(source, ConsolidationMode.WRITE);
    }

    public ClubConsolidationSummary consolidate(ImportSource source, ConsolidationMode mode) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        List<Team> sourceTeams = teamRepository.findAllTeamsBySource(source).stream()
                .sorted(Comparator.comparing(Team::getName, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(team -> team.getId().toString()))
                .toList();

        if (source == ImportSource.RFETM) {
            return new ClubConsolidationSummary(
                    source,
                    mode,
                    sourceTeams.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(new ConsolidationWarning("RFETM requires the team-folder consolidation processor")));
        }

        List<ConsolidationWarning> warnings = new ArrayList<>();
        List<ConsolidationWarning> errors = new ArrayList<>();
        Map<String, List<Team>> exactGroups = new LinkedHashMap<>();
        for (Team team : sourceTeams) {
            String key = normalizer.exactKey(source, team.getName());
            if (key.isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank normalized key for team " + team.getId()));
                continue;
            }
            exactGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(team);
        }

        List<CommonTermClubResolver.CommonTermGroup> commonTermGroups =
                commonTermClubResolver.resolve(source, exactGroups.values().stream()
                        .filter(members -> members.size() == 1)
                        .flatMap(List::stream)
                        .toList());
        Set<Team> commonTermMembers = commonTermGroups.stream()
                .flatMap(group -> group.members().stream())
                .collect(Collectors.toSet());
        exactGroups.values().removeIf(members -> members.stream().anyMatch(commonTermMembers::contains));

        List<Group> baseGroups = exactGroups.entrySet().stream()
                .map(entry -> Group.exact(entry.getKey(), entry.getValue(),
                        normalizer.preferredDisplayName(source,
                                entry.getValue().stream().map(Team::getName).toList())))
                .collect(Collectors.toCollection(ArrayList::new));
        commonTermGroups.stream()
                .map(group -> Group.commonTerm(group.normalizedTerm(), group.members(), group.canonicalDisplayName()))
                .forEach(baseGroups::add);
        baseGroups = baseGroups.stream()
                .sorted(Comparator.comparing(Group::normalizedKey).thenComparing(Group::displayName))
                .toList();

        int exactGroupsCount = (int) baseGroups.stream()
                .filter(group -> group.matchRule().equals("exact-key") && group.members().size() > 1)
                .count();
        FuzzyMergeResult fuzzyMergeResult = mergeMutualBestFuzzy(source, baseGroups, warnings);

        int clubsCreated = 0;
        int registrationsReassociated = 0;
        int alreadyCorrect = 0;
        List<ConsolidatedClub> consolidations = new ArrayList<>();

        for (Group group : fuzzyMergeResult.groups()) {
            List<Team> members = group.members().stream()
                    .sorted(Comparator.comparing(Team::getName).thenComparing(team -> team.getId().toString()))
                    .toList();
            String canonicalDisplayName = group.matchRule().equals("common-term")
                    ? group.displayName()
                    : normalizer.preferredDisplayName(source, members.stream().map(Team::getName).toList());
            if (canonicalDisplayName.isBlank()) {
                warnings.add(new ConsolidationWarning("Skipped blank canonical name for key " + group.normalizedKey()));
                continue;
            }

            Set<FederatedClub> existingDistinct = members.stream()
                    .map(team -> team.getFederatedClub().orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            /*
            if (members.size() == 1 && existingDistinct.isEmpty()) {
                warnings.add(new ConsolidationWarning(
                        "Rejected singleton team without an approved match: " + members.getFirst().getId()));
                continue;
            }*/

            if (existingDistinct.size() > 1) {
                warnings.add(new ConsolidationWarning(
                        "Conflicting existing federated clubs for key " + group.normalizedKey()));
                continue;
            }

            FederatedClub target = existingDistinct.stream().findFirst().orElse(null);
            if (target == null) {
                target = federatedClubRepository.findFederatedClubBySourceAndName(source, canonicalDisplayName).orElse(null);
            }
            if (target == null) {
                clubsCreated++;
                if (mode == ConsolidationMode.WRITE) {
                    target = FederatedClub.createNew(source, canonicalDisplayName);
                    federatedClubRepository.saveFederatedClub(target);
                } else {
                    target = FederatedClub.createExisting(UUID.randomUUID(), source, canonicalDisplayName);
                }
            }

            if (mode == ConsolidationMode.WRITE && !target.getName().equals(canonicalDisplayName)) {
                target.modifyName(canonicalDisplayName);
                federatedClubRepository.saveFederatedClub(target);
            }

            for (Team member : members) {
                UUID currentId = member.getFederatedClub().map(FederatedClub::getId).orElse(null);
                UUID targetId = target.getId();
                if (Objects.equals(currentId, targetId)) {
                    alreadyCorrect++;
                    continue;
                }
                registrationsReassociated++;
                if (mode == ConsolidationMode.WRITE) {
                    teamRepository.saveTeam(member.withFederatedClub(target));
                }
            }

            consolidations.add(new ConsolidatedClub(
                    source,
                    group.normalizedKey(),
                    canonicalDisplayName,
                    group.matchRule(),
                    group.confidence(),
                    members.stream().map(Team::getId).toList()));
        }

        ClubConsolidationSummary summary = new ClubConsolidationSummary(
                source,
                mode,
                sourceTeams.size(),
                exactGroupsCount,
                fuzzyMergeResult.acceptedFuzzyGroups(),
                clubsCreated,
                0,
                registrationsReassociated,
                alreadyCorrect,
                List.copyOf(consolidations),
                List.copyOf(warnings),
                List.copyOf(errors));
        LOGGER.info("Club consolidation finished for {} in {} mode: {}", source, mode, summary);
        return summary;
    }

    private FuzzyMergeResult mergeMutualBestFuzzy(ImportSource source,
                                                  List<Group> groups,
                                                  List<ConsolidationWarning> warnings) {
        Map<Integer, Candidate> bestCandidates = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                Group left = groups.get(i);
                Group right = groups.get(j);
                ClubNameComparison comparison = matcher.compare(source, left.displayName(), right.displayName());
                if (comparison.fuzzyCandidate()) {
                    registerCandidate(bestCandidates, i, j, comparison.score());
                    registerCandidate(bestCandidates, j, i, comparison.score());
                    continue;
                }
                if (!comparison.exact()) {
                    warnings.add(new ConsolidationWarning(rejectionReason(left, right, comparison)));
                }
            }
        }

        boolean[] consumed = new boolean[groups.size()];
        int acceptedFuzzyGroups = 0;
        List<Group> merged = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            Candidate leftBest = bestCandidates.get(i);
            if (leftBest == null || leftBest.tie()) {
                consumed[i] = true;
                merged.add(groups.get(i));
                continue;
            }
            Candidate rightBest = bestCandidates.get(leftBest.otherIndex());
            if (rightBest == null || rightBest.tie() || rightBest.otherIndex() != i || consumed[leftBest.otherIndex()]) {
                consumed[i] = true;
                merged.add(groups.get(i));
                continue;
            }

            consumed[i] = true;
            consumed[leftBest.otherIndex()] = true;
            Group mergedGroup = Group.fuzzy(groups.get(i), groups.get(leftBest.otherIndex()), leftBest.score());
            merged.add(mergedGroup);
            acceptedFuzzyGroups++;
        }

        merged.sort(Comparator.comparing(Group::normalizedKey).thenComparing(Group::displayName));
        return new FuzzyMergeResult(List.copyOf(merged), acceptedFuzzyGroups);
    }

    private static String rejectionReason(Group left, Group right, ClubNameComparison comparison) {
        return switch (comparison.classification()) {
            case REJECTED_SHORT -> "Rejected short candidate: " + left.displayName() + " <-> " + right.displayName();
            case REJECTED_TOKEN_MISMATCH -> "Rejected candidate due to tokens: " + left.displayName() + " <-> " + right.displayName();
            case REJECTED_BELOW_THRESHOLD ->
                    "Rejected candidate below threshold: " + left.displayName() + " <-> " + right.displayName()
                            + " score=" + comparison.score();
            default -> "Rejected candidate: " + left.displayName() + " <-> " + right.displayName();
        };
    }

    private static void registerCandidate(Map<Integer, Candidate> bestCandidates, int owner, int other, double score) {
        Candidate current = bestCandidates.get(owner);
        if (current == null || score > current.score()) {
            bestCandidates.put(owner, new Candidate(other, score, false));
            return;
        }
        if (Double.compare(score, current.score()) == 0) {
            bestCandidates.put(owner, new Candidate(current.otherIndex(), current.score(), true));
        }
    }

    private record Candidate(int otherIndex, double score, boolean tie) {
    }

    private record Group(String normalizedKey,
                         String displayName,
                         List<Team> members,
                         String matchRule,
                         double confidence) {
        static Group exact(String key, List<Team> members, String displayName) {
            return new Group(key, displayName, List.copyOf(members), "exact-key", 1.0d);
        }

        static Group commonTerm(String term, List<Team> members, String displayName) {
            return new Group(term, displayName, List.copyOf(members), "common-term", 1.0d);
        }

        static Group fuzzy(Group left, Group right, double confidence) {
            List<Team> combined = new ArrayList<>(left.members());
            combined.addAll(right.members());
            String key = left.normalizedKey().compareTo(right.normalizedKey()) <= 0
                    ? left.normalizedKey()
                    : right.normalizedKey();
            String display = left.displayName().compareTo(right.displayName()) <= 0
                    ? left.displayName()
                    : right.displayName();
            return new Group(key, display, List.copyOf(combined), "fuzzy-mutual-best", confidence);
        }
    }

    private record FuzzyMergeResult(List<Group> groups, int acceptedFuzzyGroups) {
    }
}
