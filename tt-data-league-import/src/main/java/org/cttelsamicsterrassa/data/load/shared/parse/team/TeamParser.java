package org.cttelsamicsterrassa.data.load.shared.parse.team;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a team participation file into parsed {@link Team} values.
 */
@Component
public class TeamParser {

    private final ObjectMapper objectMapper;

    public TeamParser() {
        this(defaultObjectMapper());
    }

    public TeamParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the given team file.
     *
     * @throws TeamParseException if the file cannot be read, is not valid UTF-8 JSON, or does not
     *                            match the team schema
     */
    public List<Team> parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            List<Team> teams = objectMapper.readerForListOf(Team.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(in);
            if (teams == null || teams.isEmpty()) {
                throw new IllegalArgumentException("team file must contain at least one team");
            }
            return List.copyOf(teams);
        } catch (IOException | RuntimeException e) {
            throw new TeamParseException(file, e);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
    }
}
