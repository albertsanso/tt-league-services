package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStats;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The statistics controller and its security together, over the in-memory fixtures of the core. */
@WebMvcTest(StatisticsController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, RunsApiWebTest.Settings.class,
        StatisticsApiWebTest.Queries.class})
class StatisticsApiWebTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String BASE = "/api/pipeline/statistics";
    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant PLAYED = Instant.parse("2026-10-03T16:00:00Z");
    private static final Instant SEEN = Instant.parse("2026-10-03T17:00:00Z");

    static final InMemoryStatisticsReadRepository READS = new InMemoryStatisticsReadRepository();
    static final InMemoryDailyStatsRepository DAILY = new InMemoryDailyStatsRepository();
    static final InMemoryMatchDayRepository MATCH_DAYS = new InMemoryMatchDayRepository();

    static {
        DAILY.upsert(List.of(new DailyStats(LocalDate.parse("2026-10-05"), PipelineSource.FCTT, 4, 1, 3,
                Duration.ofSeconds(7200), 2, NOW)));
        READS.add(new RunFacts(UUID.randomUUID(), PipelineSource.FCTT, RunStatus.SUCCEEDED,
                Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z")));
        READS.add(new StepFacts(UUID.randomUUID(), PipelineSource.FCTT, StepKind.INGEST, StepStatus.SUCCEEDED,
                "SUCCEEDED", Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T09:05:00Z"),
                new IngestHealth(3, 2, 1)));
        READS.add(new MatchFacts(UUID.randomUUID(), UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", "TERCERA",
                MatchDayState.OPEN, TrackedMatchStatus.REPORTED, PLAYED, SEEN, PLAYED.plus(Duration.ofHours(6)),
                false));
        READS.add(new MatchFacts(UUID.randomUUID(), UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", "TERCERA",
                MatchDayState.OPEN, TrackedMatchStatus.OVERDUE, NOW.minus(Duration.ofDays(3)), SEEN, null, false));
        READS.add(new CorrectionFacts(UUID.randomUUID(), PipelineSource.FCTT, Instant.parse("2026-10-06T10:00:00Z"),
                2));
        MatchDay day = MatchDay.create(UUID.randomUUID(),
                new MatchDayKey(PipelineSource.FCTT, "2026-2027", "TERCERA", 1, "1a Fase", 1),
                new MatchDayWindow(LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-09"), 1), NOW);
        MATCH_DAYS.apply(new MatchDayChangeSet(List.of(day),
                List.of(MatchTracking.first(UUID.randomUUID(), day.id(), TrackedMatchStatus.AWAITING_RESULT, PLAYED,
                        "Home", "Away", SEEN, null, null)),
                Set.of(), List.of()));
    }

    @TestConfiguration
    static class Queries {

        @Bean
        StatisticsQueries statisticsQueries() {
            return new StatisticsQueries(READS, DAILY, MATCH_DAYS, new FakeRunClock(NOW),
                    new StatisticsSettings(MADRID, LocalTime.of(0, 30), 31));
        }
    }

    @Autowired
    MockMvc mvc;

    private static String user() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), new JWTClaimsSet.Builder().subject("alice")
                .claim("roles", List.of("USER")).claim("permissions", List.of())
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        for (String path : List.of("/daily?from=2026-10-01&to=2026-10-07", "/runs?from=2026-10-01&to=2026-10-07",
                "/time-to-report?season=2026-2027", "/pending", "/corrections?from=2026-10-01&to=2026-10-07",
                "/reporting-progress?source=FCTT&season=2026-2027", "/source-health?from=2026-10-01&to=2026-10-07")) {
            mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void dailyReturnsTheStoredRowsWithTheZone() throws Exception {
        mvc.perform(get(BASE + "/daily?from=2026-10-01&to=2026-10-07&source=fctt").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.rows[0].source").value("FCTT"))
                .andExpect(jsonPath("$.rows[0].runs").value(4))
                .andExpect(jsonPath("$.rows[0].failures").value(1))
                .andExpect(jsonPath("$.rows[0].matchesReported").value(3))
                .andExpect(jsonPath("$.rows[0].avgTimeToReportSeconds").value(7200))
                .andExpect(jsonPath("$.rows[0].pendingEndOfDay").value(2));
        mvc.perform(get(BASE + "/daily?from=2026-10-01&to=2026-10-07&source=RFETM")
                        .header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void runsReturnsOutcomesAndStepAverages() throws Exception {
        mvc.perform(get(BASE + "/runs?from=2026-10-01&to=2026-10-07").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.days[0].succeeded").value(1))
                .andExpect(jsonPath("$.days[0].failed").value(0))
                .andExpect(jsonPath("$.stepAverages[0].kind").value("INGEST"))
                .andExpect(jsonPath("$.stepAverages[0].avgStepSeconds").value(300));
    }

    @Test
    void timeToReportReturnsTheSourceTotalAndTheCompetitionRows() throws Exception {
        mvc.perform(get(BASE + "/time-to-report?season=2026-2027").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value("2026-2027"))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].competition").doesNotExist())
                .andExpect(jsonPath("$.rows[0].count").value(1))
                .andExpect(jsonPath("$.rows[0].medianSeconds").value(21600))
                .andExpect(jsonPath("$.rows[1].competition").value("TERCERA"))
                .andExpect(jsonPath("$.rows[1].p90Seconds").value(21600));
    }

    @Test
    void pendingReturnsTheBucketsPerSource() throws Exception {
        mvc.perform(get(BASE + "/pending").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-10-10T08:00:00Z"))
                .andExpect(jsonPath("$.sources.length()").value(3))
                .andExpect(jsonPath("$.sources[2].source").value("FCTT"))
                .andExpect(jsonPath("$.sources[2].days2To7").value(1))
                .andExpect(jsonPath("$.sources[2].overdue").value(1));
        mvc.perform(get(BASE + "/pending?season=2026-2027&source=FCTT").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1));
    }

    @Test
    void correctionsReturnsTheDaysAndTotals() throws Exception {
        mvc.perform(get(BASE + "/corrections?from=2026-10-01&to=2026-10-07").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-10-06"))
                .andExpect(jsonPath("$.days[0].amendedPlayed").value(2))
                .andExpect(jsonPath("$.totals[0].source").value("FCTT"))
                .andExpect(jsonPath("$.totals[0].amendedPlayed").value(2));
    }

    @Test
    void sourceHealthReturnsTheDaysAndTotals() throws Exception {
        mvc.perform(get(BASE + "/source-health?from=2026-10-01&to=2026-10-07").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].httpErrors").value(3))
                .andExpect(jsonPath("$.days[0].timeouts").value(2))
                .andExpect(jsonPath("$.days[0].parseErrors").value(1))
                .andExpect(jsonPath("$.days[0].ingestAttempts").value(1))
                .andExpect(jsonPath("$.days[0].healthUnknown").value(0))
                .andExpect(jsonPath("$.totals[0].source").value("FCTT"));
    }

    @Test
    void reportingProgressReturnsTheMatchDayRowsAndPoints() throws Exception {
        mvc.perform(get(BASE + "/reporting-progress?source=FCTT&season=2026-2027&competition=TERCERA")
                        .header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FCTT"))
                .andExpect(jsonPath("$.matchDays.length()").value(1))
                .andExpect(jsonPath("$.matchDays[0].competition").value("TERCERA"))
                .andExpect(jsonPath("$.matchDays[0].round").value(1))
                .andExpect(jsonPath("$.matchDays[0].windowStart").value("2026-10-09"))
                .andExpect(jsonPath("$.matchDays[0].windowEnd").value("2026-10-10"))
                .andExpect(jsonPath("$.matchDays[0].pending").value(1))
                .andExpect(jsonPath("$.matchDays[0].points.length()").value(2))
                .andExpect(jsonPath("$.matchDays[0].points[1].pending").value(1));
    }

    @Test
    void badRangesAndParametersAre400() throws Exception {
        String auth = user();
        for (String path : List.of(
                "/daily?from=2026-10-07&to=2026-10-01",
                "/daily?from=2025-01-01&to=2026-10-01",
                "/daily?from=nope&to=2026-10-01",
                "/daily?to=2026-10-01",
                "/runs?from=2026-10-01",
                "/corrections?from=2026-10-01&to=2026-10-07&source=NOPE",
                "/source-health?to=2026-10-07",
                "/time-to-report",
                "/time-to-report?season=26-27",
                "/pending?season=bad",
                "/reporting-progress?season=2026-2027",
                "/reporting-progress?source=FCTT&source=RFETM&season=2026-2027",
                "/reporting-progress?source=FCTT",
                "/reporting-progress?source=FCTT&season=bad")) {
            mvc.perform(get(BASE + path).header("Authorization", auth))
                    .andExpect(status().isBadRequest());
        }
    }
}
