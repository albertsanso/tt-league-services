package org.cttelsamicsterrassa.data.pipeline.core.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.BcnesaCompetitionNames;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestStatusRow;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.ScopeBuilder;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.ScopeUnavailableException;
import org.junit.jupiter.api.Test;

class TrackerOpenMatchDayScopeResolverTest {

    private static final String SEASON = "2026-2027";
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final ScriptedIngestStatusGateway status = new ScriptedIngestStatusGateway();
    private final TrackerOpenMatchDayScopeResolver resolver = new TrackerOpenMatchDayScopeResolver(matchDays, status,
            new ScopeBuilder(BcnesaCompetitionNames.defaults()));

    private void openDay(int round) {
        MatchDayKey key = new MatchDayKey(PipelineSource.FCTT, SEASON, "tercera-masculino", 1, "1a Fase", round);
        MatchDay day = MatchDay.create(UUID.randomUUID(), key,
                new MatchDayWindow(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-04"), 2), NOW).open(NOW);
        MatchTracking match = MatchTracking.first(UUID.randomUUID(), day.id(), TrackedMatchStatus.AWAITING_RESULT,
                NOW, "H", "A", NOW, null, null);
        matchDays.apply(new MatchDayChangeSet(List.of(day), List.of(match), Set.of(), List.of()));
    }

    private void statusRow(int round) {
        status.returning(new IngestMatchDayStatus(PipelineSource.FCTT, SEASON, List.of(new IngestStatusRow(SEASON,
                "tercera", "G1", "1a Fase", "male", "Barcelona", round, "scheduled", null, null))));
    }

    @Test
    void resolvesTheFiltersOfTheOpenMatchDays() {
        openDay(4);
        statusRow(4);

        RunScope scope = resolver.resolve(PipelineSource.FCTT, SEASON);

        assertThat(scope.filters()).containsExactly(
                new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(4)));
    }

    @Test
    void theManualResolverNeverLimitsTheOpenMatchDaysToTheRecentOnes() {
        List<IngestStatusRow> rows = new java.util.ArrayList<>();
        for (int round = 1; round <= 5; round++) {
            openDay(round);
            rows.add(new IngestStatusRow(SEASON, "tercera", "G1", "1a Fase", "male", "Barcelona", round, "scheduled",
                    null, null));
        }
        status.returning(new IngestMatchDayStatus(PipelineSource.FCTT, SEASON, rows));

        RunScope scope = resolver.resolve(PipelineSource.FCTT, SEASON);

        assertThat(scope.filters()).containsExactly(
                new ScopeFilter("tercera", "G1", "1a Fase", "Barcelona", "male", List.of(1, 2, 3, 4, 5)));
    }

    @Test
    void nothingOpenIsNoOpenMatchDaysWithoutCallingTheIngest() {
        assertThatThrownBy(() -> resolver.resolve(PipelineSource.FCTT, SEASON))
                .isInstanceOfSatisfying(ScopeUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo(ScopeUnavailableException.NO_OPEN_MATCH_DAYS));
        assertThat(status.requested).isEmpty();
    }

    @Test
    void aMissingStatusFileIsNoIngestStatus() {
        openDay(4);

        assertThatThrownBy(() -> resolver.resolve(PipelineSource.FCTT, SEASON))
                .isInstanceOfSatisfying(ScopeUnavailableException.class, e -> {
                    assertThat(e.code()).isEqualTo(TrackerOpenMatchDayScopeResolver.NO_INGEST_STATUS);
                    assertThat(e.getMessage()).contains("FCTT", SEASON);
                });
    }

    @Test
    void anUnmatchedOpenDayIsScopeUnmatched() {
        openDay(4);
        statusRow(5);

        assertThatThrownBy(() -> resolver.resolve(PipelineSource.FCTT, SEASON))
                .isInstanceOfSatisfying(ScopeUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("SCOPE_UNMATCHED"));
    }

    @Test
    void gatewayFailuresPropagate() {
        openDay(4);
        status.failWith(new GatewayException(GatewayException.Kind.UNAVAILABLE, 503, "down"));

        assertThatThrownBy(() -> resolver.resolve(PipelineSource.FCTT, SEASON)).isInstanceOf(GatewayException.class);
    }
}
