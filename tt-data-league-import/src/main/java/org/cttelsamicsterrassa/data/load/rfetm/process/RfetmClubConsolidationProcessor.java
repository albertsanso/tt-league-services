package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidatedClub;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationMode;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ConsolidationWarning;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.MatchingMode;
import org.cttelsamicsterrassa.data.load.shared.parse.team.Team;
import org.cttelsamicsterrassa.data.load.shared.parse.team.TeamParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RfetmClubConsolidationProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RfetmClubConsolidationProcessor.class);

    private static final Pattern TEAMS_FILE_NAME_PATTERN = Pattern.compile("\\d{4}-\\d{4}\\.json");

    private final FederatedClubRepository clubRepository;
    private final TeamRepository teamRepository;

    private final TeamParser teamParser;

    @Inject
    public RfetmClubConsolidationProcessor(FederatedClubRepository clubRepository, TeamRepository teamRepository, TeamParser teamParser) {
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.teamParser = teamParser;
    }

    public ClubConsolidationSummary process(Path teamsFolderPath) {
        return process(teamsFolderPath, null);
    }

    public ClubConsolidationSummary process(Path teamsFolderPath, String season) {
        return process(teamsFolderPath, season, ConsolidationMode.WRITE);
    }

    public ClubConsolidationSummary process(Path teamsFolderPath, String season, ConsolidationMode mode) {
        Objects.requireNonNull(teamsFolderPath, "teamsFolderPath");
        Objects.requireNonNull(mode, "mode");
        Map<SeasonTeamNameKey, String> clubNamesDictionary = loadClubAndTeamNamesDictionary(teamsFolderPath, season);
        List<org.cttelsamicsterrassa.data.core.domain.club.model.Team> registrations =
                teamRepository.findAllTeamsBySource(ImportSource.RFETM);
        ClubConsolidationSummary.Builder summary = ClubConsolidationSummary.builder(ImportSource.RFETM)
                .scannedRegistrations(registrations.size());
        Map<String, ClubResolution> clubsByName = new HashMap<>();
        Map<UUID, FederatedClub> clubs = new HashMap<>();
        Map<UUID, List<org.cttelsamicsterrassa.data.core.domain.club.model.Team>> associatedTeams = new HashMap<>();
        Map<UUID, Boolean> changedClubs = new HashMap<>();

        registrations.forEach(team -> {
            String clubName = clubNamesDictionary.get(new SeasonTeamNameKey(team.getSeason(), team.getName()));
            if (clubName != null) {
                ClubResolution resolution = clubsByName.computeIfAbsent(
                        clubName, name -> findOrCreateClub(name, team.getSeason(), mode, summary));
                FederatedClub club = resolution.club();
                clubs.put(club.getId(), club);
                associatedTeams.computeIfAbsent(club.getId(), ignored -> new ArrayList<>()).add(team);
                if (resolution.created()) {
                    changedClubs.put(club.getId(), true);
                }
                if (team.getFederatedClub().map(associatedClub -> associatedClub.getId().equals(club.getId())).orElse(false)) {
                    summary.incrementAlreadyCorrect();
                    return;
                }
                if (mode == ConsolidationMode.WRITE) {
                    teamRepository.saveTeam(team.withFederatedClub(club));
                }
                summary.incrementReassociated();
                changedClubs.put(club.getId(), true);
                LOGGER.info("Reassociated RFETM team {} ({}) to club {} ({})",
                        team.getId(), team.getName(), club.getId(), club.getName());
            } else {
                summary.warning(new ConsolidationWarning(ImportSource.RFETM, "No club name found for team",
                        List.of(team.getId()), List.of(team.getName())));
                LOGGER.warn("No club name found for team: {} in season: {}", team.getName(), team.getSeason());
            }
        });

        for (Map.Entry<UUID, List<org.cttelsamicsterrassa.data.core.domain.club.model.Team>> entry
                : associatedTeams.entrySet()) {
            if (changedClubs.getOrDefault(entry.getKey(), false)) {
                FederatedClub club = clubs.get(entry.getKey());
                List<org.cttelsamicsterrassa.data.core.domain.club.model.Team> teams = entry.getValue();
                summary.consolidation(new ConsolidatedClub(
                        ImportSource.RFETM,
                        club.getName(),
                        club.getId(),
                        MatchingMode.EXACT,
                        teams.stream().map(org.cttelsamicsterrassa.data.core.domain.club.model.Team::getId).toList(),
                        teams.stream().map(org.cttelsamicsterrassa.data.core.domain.club.model.Team::getName).toList()));
            }
        }
        ClubConsolidationSummary result = summary.build();
        LOGGER.info("RFETM club consolidation: {}", result);
        return result;
    }

    private Map<SeasonTeamNameKey, String> loadClubAndTeamNamesDictionary(Path teamsFolderPath, String season) {
        List<Team> teamsListForAllSeasons = loadListOfTeams(teamsFolderPath, season);
        return buildClubNamesMap(teamsListForAllSeasons);
    }

    private List<Team> loadListOfTeams(Path teamsFolderPath, String season) {
        List<Team> teams = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(teamsFolderPath)) {
            for (Path pathEntry : stream) {
                String fileName = pathEntry.getFileName().toString();
                if ((season == null && TEAMS_FILE_NAME_PATTERN.matcher(fileName).matches())
                        || (season != null && fileName.equals("%s.json".formatted(season)))) {
                    teams.addAll(teamParser.parse(pathEntry));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return teams;
    }

    private ClubResolution findOrCreateClub(String clubName,
                                            Season season,
                                            ConsolidationMode mode,
                                            ClubConsolidationSummary.Builder summary) {
        return clubRepository.findFederatedClubBySourceAndName(ImportSource.RFETM, clubName)
                .map(club -> new ClubResolution(club, false))
                .orElseGet(() -> {
                    LOGGER.warn("No club found for club name: {} in season: {}", clubName, season);
                    FederatedClub newClub = FederatedClub.createNew(ImportSource.RFETM, clubName);
                    if (mode == ConsolidationMode.WRITE) {
                        clubRepository.saveFederatedClub(newClub);
                    }
                    summary.incrementClubsCreated();
                    return new ClubResolution(newClub, true);
                });
    }

    private Map<SeasonTeamNameKey, String> buildClubNamesMap(List<Team> teams) {
        return teams.stream()
                .collect(java.util.stream.Collectors.toMap(
                        team -> new SeasonTeamNameKey(Season.fromFormatted(team.season()), team.teamName()),
                        Team::clubName,
                        (existing, replacement) -> existing // In case of duplicates, keep the existing value
                ));
    }

    private record SeasonTeamNameKey(Season season, String teamName) {}

    private record ClubResolution(FederatedClub club, boolean created) {}

}
