package org.cttelsamicsterrassa.data.load.rfetm.process;

import org.cttelsamicsterrassa.data.core.domain.club.model.Club;
import org.cttelsamicsterrassa.data.core.domain.club.model.Team;
import org.cttelsamicsterrassa.data.core.domain.club.repository.ClubRepository;
import org.cttelsamicsterrassa.data.core.domain.club.repository.TeamRepository;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.process.InMemoryRepositories;
import org.cttelsamicsterrassa.data.load.shared.club.consolidate.ClubConsolidationSummary;
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
        CountingClubRepository clubs = new CountingClubRepository();
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
        assertEquals(2, clubs.saveClubCalls);
        assertEquals(3, summary.scannedRegistrations());
        assertEquals(2, summary.clubsCreated());
        assertEquals(3, summary.registrationsReassociated());
        assertEquals(0, summary.alreadyCorrectRegistrations());
        assertEquals(2, summary.consolidations().size());
        assertEquals(2, teams.findAllTeamsBySource(ImportSource.RFETM).stream()
                .map(team -> team.getClub().orElseThrow().getId())
                .distinct()
                .count());
    }

    private static final class CountingClubRepository implements ClubRepository {
        private final InMemoryRepositories.Clubs delegate = new InMemoryRepositories.Clubs();
        private int findBySourceAndNameCalls;
        private int saveClubCalls;

        @Override
        public Optional<Club> findClubById(UUID id) {
            return delegate.findClubById(id);
        }

        @Override
        public Optional<Club> findClubByName(String name) {
            return delegate.findClubByName(name);
        }

        @Override
        public Optional<Club> findClubBySourceAndName(ImportSource source, String name) {
            findBySourceAndNameCalls++;
            return delegate.findClubBySourceAndName(source, name);
        }

        @Override
        public List<Club> findAllClubsByFragmentsInName(List<String> fragments) {
            return delegate.findAllClubsByFragmentsInName(fragments);
        }

        @Override
        public List<Club> findAllClubsBySourceAndFragmentsInName(
                ImportSource source, List<String> fragments) {
            return delegate.findAllClubsBySourceAndFragmentsInName(source, fragments);
        }

        @Override
        public void saveClub(Club club) {
            saveClubCalls++;
            delegate.saveClub(club);
        }

        @Override
        public void deleteClubById(UUID id) {
            delegate.deleteClubById(id);
        }
    }
}
