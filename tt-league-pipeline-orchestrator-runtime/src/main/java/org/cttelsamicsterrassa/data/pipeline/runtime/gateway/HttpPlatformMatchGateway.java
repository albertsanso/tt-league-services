package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar.PlatformCalendarMatch;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress.PlatformJornada;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link PlatformMatchGateway} over the platform match API (needs the {@code matches:read} authority). Round
 * progress is read without {@code onlyOpen} so tracked match days the platform no longer reports as open can still be
 * closed; the calendar is read per competition and includes undated matches. Only the fields the tracker needs are
 * mapped; unknown fields are ignored.
 */
public final class HttpPlatformMatchGateway implements PlatformMatchGateway {

    static final String ROUND_PROGRESS = "/api/v1/match/round-progress";
    static final String CALENDAR = "/api/v1/match/calendar";

    private final RestClient client;
    private final ObjectMapper json;

    public HttpPlatformMatchGateway(RestClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public PlatformRoundProgress roundProgress(PipelineSource source, String season) {
        String raw = get(ROUND_PROGRESS, source, season, null);
        RoundProgressBody body = parse(raw, RoundProgressBody.class, ROUND_PROGRESS);
        if (body.today() == null || body.overdueGraceDays() == null || body.groups() == null) {
            throw GatewayErrors.protocol("GET", ROUND_PROGRESS, "today, overdueGraceDays and groups are required",
                    null);
        }
        try {
            List<PlatformJornada> jornadas = new ArrayList<>();
            for (GroupBody group : body.groups()) {
                if (group.rounds() == null) {
                    throw GatewayErrors.protocol("GET", ROUND_PROGRESS, "a group has no rounds", null);
                }
                for (RoundBody round : group.rounds()) {
                    if (round.round() == null || round.scheduledMatches() == null || round.playedMatches() == null
                            || round.open() == null) {
                        throw GatewayErrors.protocol("GET", ROUND_PROGRESS,
                                "round, scheduledMatches, playedMatches and open are required", null);
                    }
                    jornadas.add(new PlatformJornada(group.competition(), group.groupNumber(), group.phase(),
                            round.round(), date(round.firstDate()), date(round.lastDate()),
                            Math.toIntExact(round.scheduledMatches()), Math.toIntExact(round.playedMatches()),
                            round.open()));
                }
            }
            return new PlatformRoundProgress(LocalDate.parse(body.today()), body.overdueGraceDays(), jornadas);
        } catch (DateTimeException | ArithmeticException | IllegalArgumentException e) {
            throw GatewayErrors.protocol("GET", ROUND_PROGRESS, "unreadable value: " + e.getMessage(), e);
        }
    }

    @Override
    public PlatformCompetitionCalendar competitionCalendar(PipelineSource source, String season, String competition) {
        String raw = get(CALENDAR, source, season, competition);
        CalendarBody body = parse(raw, CalendarBody.class, CALENDAR);
        if (body.today() == null || body.groups() == null) {
            throw GatewayErrors.protocol("GET", CALENDAR, "today and groups are required", null);
        }
        try {
            List<PlatformCalendarMatch> matches = new ArrayList<>();
            for (CalendarGroupBody group : body.groups()) {
                if (group.rounds() == null) {
                    throw GatewayErrors.protocol("GET", CALENDAR, "a group has no rounds", null);
                }
                for (CalendarRoundBody round : group.rounds()) {
                    if (round.round() == null || round.matches() == null) {
                        throw GatewayErrors.protocol("GET", CALENDAR, "round and matches are required", null);
                    }
                    for (CalendarMatchBody match : round.matches()) {
                        if (match.id() == null || match.status() == null || match.calendarState() == null) {
                            throw GatewayErrors.protocol("GET", CALENDAR,
                                    "a match needs id, status and calendarState", null);
                        }
                        matches.add(new PlatformCalendarMatch(match.id(), competition, group.groupNumber(),
                                group.phase(), round.round(), instant(match.dateTime()), match.homeTeamName(),
                                match.awayTeamName(), match.status(), match.calendarState(), match.homeGamesWon(),
                                match.awayGamesWon(), match.winnerTeamName()));
                    }
                }
            }
            return new PlatformCompetitionCalendar(LocalDate.parse(body.today()), matches);
        } catch (DateTimeException | IllegalArgumentException e) {
            throw GatewayErrors.protocol("GET", CALENDAR, "unreadable value: " + e.getMessage(), e);
        }
    }

    private String get(String path, PipelineSource source, String season, String competition) {
        try {
            String raw = client.get().uri(uriBuilder -> {
                uriBuilder.path(path).queryParam("source", "{source}").queryParam("season", "{season}");
                if (competition == null) {
                    return uriBuilder.build(Map.of("source", source.name(), "season", season));
                }
                uriBuilder.queryParam("competition", "{competition}");
                return uriBuilder.build(
                        Map.of("source", source.name(), "season", season, "competition", competition));
            }).retrieve().body(String.class);
            if (raw == null || raw.isBlank()) {
                throw GatewayErrors.protocol("GET", path, "empty response body", null);
            }
            return raw;
        } catch (RestClientException e) {
            throw GatewayErrors.translate("GET", path, e);
        }
    }

    private <T> T parse(String raw, Class<T> type, String path) {
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException e) {
            throw GatewayErrors.protocol("GET", path, "the response is not valid JSON", e);
        }
    }

    private static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static Instant instant(String value) {
        return value == null ? null : ZonedDateTime.parse(value).toInstant();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoundProgressBody(String today, Integer overdueGraceDays, List<GroupBody> groups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroupBody(String competition, Integer groupNumber, String phase, List<RoundBody> rounds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoundBody(
            Integer round,
            String firstDate,
            String lastDate,
            Long scheduledMatches,
            Long playedMatches,
            Boolean open) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CalendarBody(String today, List<CalendarGroupBody> groups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CalendarGroupBody(Integer groupNumber, String phase, List<CalendarRoundBody> rounds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CalendarRoundBody(Integer round, List<CalendarMatchBody> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CalendarMatchBody(
            UUID id,
            String dateTime,
            String homeTeamName,
            String awayTeamName,
            String status,
            String calendarState,
            Integer homeGamesWon,
            Integer awayGamesWon,
            String winnerTeamName) {
    }
}
