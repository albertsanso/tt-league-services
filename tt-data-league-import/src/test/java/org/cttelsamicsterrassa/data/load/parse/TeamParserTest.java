package org.cttelsamicsterrassa.data.load.parse;

import org.cttelsamicsterrassa.data.load.shared.parse.team.Team;
import org.cttelsamicsterrassa.data.load.shared.parse.team.TeamParseException;
import org.cttelsamicsterrassa.data.load.shared.parse.team.TeamParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamParserTest {

    private final TeamParser parser = new TeamParser();

    @Test
    void parsesTeamParticipationsAndMapsSchemaNames(@TempDir Path tempDir) throws IOException {
        Path file = writeTeamFile(tempDir, """
                [
                  {
                    "season": "2024-2025",
                    "club_name": "CLUB TENNIS TAULA OLOT",
                    "team_name": "C.T.T. OLOT - CAPDEVILA PERMAR",
                    "category": "FF-DHF-1/2"
                  },
                  {
                    "season": "2025-2026",
                    "club_name": "CLUB DEPORTIVO INDIANA GAMES",
                    "team_name": "CLINIQAS.COM",
                    "category": "SUM"
                  }
                ]
                """);

        List<Team> teams = parser.parse(file);

        assertEquals(2, teams.size());
        assertEquals("CLUB TENNIS TAULA OLOT", teams.getFirst().clubName());
        assertEquals("C.T.T. OLOT - CAPDEVILA PERMAR", teams.getFirst().teamName());
        assertEquals("FF-DHF-1/2", teams.getFirst().category());
        assertEquals("2025-2026", teams.get(1).season());
    }

    @Test
    void rejectsEmptyFilesAndInvalidTeamEntries(@TempDir Path tempDir) throws IOException {
        assertThrows(TeamParseException.class, () -> parser.parse(writeTeamFile(tempDir, "[]")));
        assertThrows(TeamParseException.class, () -> parser.parse(writeTeamFile(tempDir, """
                [
                  {
                    "season": "2024/2025",
                    "club_name": "Club",
                    "team_name": "Team",
                    "category": "SUM"
                  }
                ]
                """)));
    }

    @Test
    void rejectsAdditionalProperties(@TempDir Path tempDir) throws IOException {
        TeamParseException failure = assertThrows(TeamParseException.class, () -> parser.parse(writeTeamFile(tempDir, """
                [
                  {
                    "season": "2024-2025",
                    "club_name": "Club",
                    "team_name": "Team",
                    "category": "SUM",
                    "unexpected": "value"
                  }
                ]
                """)));

        assertTrue(failure.getCause().getMessage().contains("unexpected"));
    }

    @Test
    void reportsTheFileWhenContentIsNotValidTeamJson(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("teams.json");
        Files.writeString(broken, "{ not json");

        TeamParseException failure = assertThrows(TeamParseException.class, () -> parser.parse(broken));

        assertEquals(broken, failure.getFile());
    }

    private static Path writeTeamFile(Path tempDir, String content) throws IOException {
        Path file = tempDir.resolve("teams.json");
        Files.writeString(file, content);
        return file;
    }
}
