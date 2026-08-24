package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.FederatedClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.FederatedClubToCanonicalClubConsolidationProcessor;
import org.cttelsamicsterrassa.data.load.shared.parse.team.TeamParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RfetmClubConsolidationProcessorTest {

    @Test
    void looksUpAndCreatesEachClubOnlyOnce(@TempDir Path teamsFolder) throws IOException {
        CountingFederatedClubRepository clubs = new CountingFederatedClubRepository();
        InMemoryRepositories.Teams teams = new InMemoryRepositories.Teams();
        teams.saveTeam(Team.createNew(ImportSource.RFETM, "CLUB A TEAM", Season.of(2023), null));
        teams.saveTeam(Team.createNew(ImportSource.RFETM, "CLUB B TEAM", Season.of(2023), null));
        teams.saveTeam(Team.createNew(ImportSource.RFETM, "CLUB C TEAM", Season.of(2023), null));

        Files.writeString(teamsFolder.resolve("2023-2024.json"), """
                [
                  {
                    "season": "2023-2024",
                    "club_name": "CLUB A",
                    "team_name": "CLUB A TEAM",
                    "category": "MEN-A"
                  },
                  {
                    "season": "2023-2024",
                    "club_name": "CLUB A",
                    "team_name": "CLUB B TEAM",
                    "category": "MEN-B"
                  },
                  {
                    "season": "2023-2024",
                    "club_name": "CLUB B",
                    "team_name": "CLUB C TEAM",
                    "category": "MEN-C"
                  }
                ]
                """);

        ClubConsolidationSummary summary =
                new RfetmClubConsolidationProcessor(clubs, teams, new TeamParser()).process(teamsFolder);

        assertEquals(2, clubs.findBySourceAndNameCalls);
        assertEquals(2, clubs.saveFederatedClubCalls);
        assertEquals(3, summary.scannedRegistrations());
        assertEquals(2, summary.clubsCreated());
        assertEquals(3, summary.registrationsReassociated());
        assertEquals(0, summary.alreadyCorrectRegistrations());
        assertEquals(2, summary.consolidations().size());
        assertEquals(2, teams.findAllTeamsBySource(ImportSource.RFETM).stream()
                .map(team -> team.getFederatedClub().orElseThrow().getId())
                .distinct()
                .count());
    }

    @Test
    void linksEachCreatedRfetmClubToCanonicalIdentityOnce(@TempDir Path teamsFolder) throws IOException {
        InMemoryRepositories.Clubs clubs = new InMemoryRepositories.Clubs();
        InMemoryRepositories.CanonicalClubs canonicalClubs = new InMemoryRepositories.CanonicalClubs();
        InMemoryRepositories.Teams teams = new InMemoryRepositories.Teams();
        teams.saveTeam(Team.createNew(ImportSource.RFETM, "CLUB A TEAM", Season.of(2023), null));
        teams.saveTeam(Team.createNew(ImportSource.RFETM, "CLUB B TEAM", Season.of(2023), null));

        Files.writeString(teamsFolder.resolve("2023-2024.json"), """
                [
                  {
                    "season": "2023-2024",
                    "club_name": "CLUB A",
                    "team_name": "CLUB A TEAM",
                    "category": "MEN-A"
                  },
                  {
                    "season": "2023-2024",
                    "club_name": "CLUB A",
                    "team_name": "CLUB B TEAM",
                    "category": "MEN-B"
                  }
                ]
                """);

        ClubConsolidationSummary summary = new RfetmClubConsolidationProcessor(
                clubs, teams, new TeamParser()).process(teamsFolder);
        ClubConsolidationSummary canonicalSummary =
                new FederatedClubToCanonicalClubConsolidationProcessor(clubs, canonicalClubs)
                        .consolidate(ImportSource.RFETM);

        assertEquals(1, canonicalSummary.canonicalLinksCreated());
        assertEquals(1, canonicalClubs.size());
        assertEquals(1, clubs.findAllFederatedClubsBySourceAndFragmentsInName(
                ImportSource.RFETM, List.of("CLUB A")).size());
    }

    private static final class CountingFederatedClubRepository implements FederatedClubRepository {
        private final InMemoryRepositories.Clubs delegate = new InMemoryRepositories.Clubs();
        private int findBySourceAndNameCalls;
        private int saveFederatedClubCalls;

        @Override
        public Optional<FederatedClub> findFederatedClubById(UUID id) {
            return delegate.findFederatedClubById(id);
        }

        @Override
        public Optional<FederatedClub> findFederatedClubBySourceAndName(ImportSource source, String name) {
            findBySourceAndNameCalls++;
            return delegate.findFederatedClubBySourceAndName(source, name);
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsByFragmentsInName(List<String> fragments) {
            return delegate.findAllFederatedClubsByFragmentsInName(fragments);
        }

        @Override
        public List<FederatedClub> findAllFederatedClubsBySourceAndFragmentsInName(
                ImportSource source, List<String> fragments) {
            return delegate.findAllFederatedClubsBySourceAndFragmentsInName(source, fragments);
        }

        @Override
        public void saveFederatedClub(FederatedClub club) {
            saveFederatedClubCalls++;
            delegate.saveFederatedClub(club);
        }

        @Override
        public void deleteFederatedClubById(UUID id) {
            delegate.deleteFederatedClubById(id);
        }
    }
}
