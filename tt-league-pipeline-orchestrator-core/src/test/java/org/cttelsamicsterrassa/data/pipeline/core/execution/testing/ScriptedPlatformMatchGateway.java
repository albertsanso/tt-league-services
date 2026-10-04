package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar.PlatformCalendarMatch;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress.PlatformJornada;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serves a mutable platform snapshot and records the calendar reads. */
public class ScriptedPlatformMatchGateway implements PlatformMatchGateway {

    private LocalDate today = LocalDate.parse("2026-10-04");
    private LocalDate calendarToday;
    private int graceDays = 2;
    private RuntimeException failure;

    public final List<PlatformJornada> jornadas = new ArrayList<>();
    public final Map<String, List<PlatformCalendarMatch>> calendars = new LinkedHashMap<>();
    public final List<String> calendarReads = new ArrayList<>();

    public ScriptedPlatformMatchGateway today(LocalDate value) {
        this.today = value;
        return this;
    }

    /** Makes the calendar report a different today than round progress. */
    public ScriptedPlatformMatchGateway calendarToday(LocalDate value) {
        this.calendarToday = value;
        return this;
    }

    public ScriptedPlatformMatchGateway graceDays(int value) {
        this.graceDays = value;
        return this;
    }

    public ScriptedPlatformMatchGateway failWith(GatewayException.Kind kind, int status) {
        this.failure = new GatewayException(kind, status, "platform failed " + status);
        return this;
    }

    public ScriptedPlatformMatchGateway recover() {
        this.failure = null;
        return this;
    }

    public ScriptedPlatformMatchGateway clear() {
        jornadas.clear();
        calendars.clear();
        return this;
    }

    /** Adds a jornada and its matches; the jornada counts are derived from the matches. */
    public ScriptedPlatformMatchGateway jornada(
            String competition,
            Integer group,
            String phase,
            int round,
            LocalDate first,
            LocalDate last,
            boolean open,
            PlatformCalendarMatch... matches) {
        int played = (int) java.util.Arrays.stream(matches).filter(m -> "PLAYED".equals(m.calendarState())).count();
        jornadas.add(new PlatformJornada(
                competition, group, phase, round, first, last, matches.length - played, played, open));
        if (competition != null) {
            calendars.computeIfAbsent(competition, c -> new ArrayList<>()).addAll(List.of(matches));
        }
        return this;
    }

    public static PlatformCalendarMatch match(
            UUID id, String competition, Integer group, String phase, int round, String calendarState) {
        String status = "PLAYED".equals(calendarState) ? "PLAYED" : "SCHEDULED";
        return new PlatformCalendarMatch(id, competition, group, phase, round,
                Instant.parse("2026-10-03T16:00:00Z"), "Home " + id.toString().substring(0, 4),
                "Away " + id.toString().substring(0, 4), status, calendarState);
    }

    @Override
    public PlatformRoundProgress roundProgress(PipelineSource source, String season) {
        throwIfFailing();
        return new PlatformRoundProgress(today, graceDays, new ArrayList<>(jornadas));
    }

    @Override
    public PlatformCompetitionCalendar competitionCalendar(PipelineSource source, String season, String competition) {
        throwIfFailing();
        calendarReads.add(competition);
        return new PlatformCompetitionCalendar(
                calendarToday != null ? calendarToday : today, calendars.getOrDefault(competition, List.of()));
    }

    private void throwIfFailing() {
        if (failure != null) {
            throw failure;
        }
    }
}
