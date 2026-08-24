package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidatedClub;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationWarning;
import org.cttelsamicsterrassa.data.load.shared.parse.team.TeamParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class RfetmClubConsolidationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmClubConsolidationProcessor.class);

    private final FederatedClubRepository federatedClubRepository;
    private final TeamRepository teamRepository;
    private final TeamParser teamParser;
    @Autowired
    public RfetmClubConsolidationProcessor(FederatedClubRepository federatedClubRepository,
                                           TeamRepository teamRepository,
                                           TeamParser teamParser) {
        this.federatedClubRepository = federatedClubRepository;
        this.teamRepository = teamRepository;
        this.teamParser = teamParser;
    }

    public ClubConsolidationSummary process(Path teamsFolder) {
        return process(teamsFolder, ConsolidationMode.WRITE);
    }

    public ClubConsolidationSummary process(Path teamsFolder, ConsolidationMode mode) {
        Objects.requireNonNull(mode, "mode must not be null");
        List<ConsolidationWarning> warnings = new ArrayList<>();
        List<ConsolidationWarning> errors = new ArrayList<>();
        List<ConsolidatedClub> consolidations = new ArrayList<>();
        int scanned = 0;
        int clubsCreated = 0;
        int canonicalLinksCreated = 0;
        int reassociated = 0;
        int alreadyCorrect = 0;
        List<String> createdClubNames = new ArrayList<>();

        List<Team> sourceTeams = teamRepository.findAllTeamsBySource(ImportSource.RFETM);
        Map<String, List<Team>> teamsByName = new LinkedHashMap<>();
        for (Team team : sourceTeams) {
            teamsByName.computeIfAbsent(team.getName(), ignored -> new ArrayList<>()).add(team);
        }

        Map<String, FederatedClub> clubCache = new LinkedHashMap<>();
        Map<String, List<Team>> membersByClub = new LinkedHashMap<>();

        for (Path file : listTeamFiles(teamsFolder, warnings)) {
            List<org.cttelsamicsterrassa.data.load.shared.parse.team.Team> parsedRows;
            try {
                parsedRows = teamParser.parse(file);
            } catch (RuntimeException e) {
                warnings.add(new ConsolidationWarning("Skipping unreadable team file " + file + ": " + e.getMessage()));
                continue;
            }
            for (org.cttelsamicsterrassa.data.load.shared.parse.team.Team row : parsedRows) {
                scanned++;
                String clubName = row.clubName();
                String teamName = row.teamName();

                FederatedClub federatedClub = clubCache.get(clubName);
                if (!clubCache.containsKey(clubName)) {
                    federatedClub = federatedClubRepository
                            .findFederatedClubBySourceAndName(ImportSource.RFETM, clubName)
                            .orElse(null);
                    if (federatedClub == null) {
                        createdClubNames.add(clubName);
                        if (mode == ConsolidationMode.WRITE) {
                            federatedClub = FederatedClub.createNew(ImportSource.RFETM, clubName);
                            federatedClubRepository.saveFederatedClub(federatedClub);
                        } else {
                            federatedClub = FederatedClub.createExisting(
                                    java.util.UUID.randomUUID(), ImportSource.RFETM, clubName);
                        }
                    }
                    clubCache.put(clubName, federatedClub);
                }

                List<Team> matches = teamsByName.getOrDefault(teamName, List.of());
                if (matches.isEmpty()) {
                    warnings.add(new ConsolidationWarning("No RFETM team registration found for " + teamName));
                }
                if (federatedClub != null) {
                    membersByClub.computeIfAbsent(clubName, ignored -> new ArrayList<>()).addAll(matches);
                }

                FederatedClub resolvedClub = federatedClub;
                for (Team match : matches) {
                    if (resolvedClub == null) {
                        continue;
                    }
                    if (match.getFederatedClub().map(existing -> Objects.equals(existing.getId(), resolvedClub.getId())).orElse(false)) {
                        alreadyCorrect++;
                        continue;
                    }
                    reassociated++;
                    if (mode == ConsolidationMode.WRITE) {
                        teamRepository.saveTeam(match.withFederatedClub(resolvedClub));
                    }
                }
            }
        }

        clubsCreated = (int) createdClubNames.stream().distinct().count();

        for (Map.Entry<String, List<Team>> entry : membersByClub.entrySet()) {
            List<Team> members = entry.getValue().stream()
                    .map(Team::getId)
                    .distinct()
                    .map(id -> sourceTeams.stream().filter(team -> team.getId().equals(id)).findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
            consolidations.add(new ConsolidatedClub(
                    ImportSource.RFETM,
                    entry.getKey(),
                    entry.getKey(),
                    "team-folder",
                    1.0d,
                    members.stream().map(Team::getId).toList()));
        }
        consolidations.sort(Comparator.comparing(ConsolidatedClub::canonicalDisplayName));

        ClubConsolidationSummary summary = new ClubConsolidationSummary(
                ImportSource.RFETM,
                mode,
                scanned,
                0,
                0,
                clubsCreated,
                canonicalLinksCreated,
                reassociated,
                alreadyCorrect,
                List.copyOf(consolidations),
                List.copyOf(warnings),
                List.copyOf(errors));
        LOGGER.info("RFETM team-folder consolidation finished for {} in {} mode: {}", teamsFolder, mode, summary);
        return summary;
    }

    private static List<Path> listTeamFiles(Path teamsFolder, List<ConsolidationWarning> warnings) {
        if (teamsFolder == null || !Files.isDirectory(teamsFolder)) {
            warnings.add(new ConsolidationWarning("RFETM teams folder is not a directory: " + teamsFolder));
            return List.of();
        }
        try (Stream<Path> stream = Files.list(teamsFolder)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            warnings.add(new ConsolidationWarning("Cannot list RFETM team files in " + teamsFolder + ": " + e.getMessage()));
            return List.of();
        }
    }
}
