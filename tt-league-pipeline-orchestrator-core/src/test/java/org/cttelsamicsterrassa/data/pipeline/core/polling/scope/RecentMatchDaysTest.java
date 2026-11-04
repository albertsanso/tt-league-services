package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;
import org.junit.jupiter.api.Test;

class RecentMatchDaysTest {

    private static final String SEASON = "2026-2027";
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static OpenMatchDay day(String competition, Integer group, String phase, int round) {
        MatchDayKey key = new MatchDayKey(PipelineSource.FCTT, SEASON, competition, group, phase, round);
        return new OpenMatchDay(key, List.of(MatchTracking.first(UUID.randomUUID(), UUID.randomUUID(),
                TrackedMatchStatus.AWAITING_RESULT, NOW, "H", "A", NOW, null, null)));
    }

    private static List<Integer> rounds(List<OpenMatchDay> days) {
        return days.stream().map(day -> day.key().round()).toList();
    }

    @Test
    void oneGroupKeepsTheHighestRounds() {
        List<OpenMatchDay> open = List.of(day("tercera-masculino", 1, "1a Fase", 1),
                day("tercera-masculino", 1, "1a Fase", 2), day("tercera-masculino", 1, "1a Fase", 3),
                day("tercera-masculino", 1, "1a Fase", 4), day("tercera-masculino", 1, "1a Fase", 5));

        assertThat(rounds(RecentMatchDays.limit(open, 3))).containsExactly(3, 4, 5);
    }

    @Test
    void groupsAreLimitedIndependently() {
        List<OpenMatchDay> open = List.of(day("tercera-masculino", 1, "1a Fase", 1),
                day("tercera-femenino", 1, "1a Fase", 7), day("tercera-masculino", 1, "1a Fase", 2),
                day("tercera-femenino", 1, "1a Fase", 8), day("tercera-masculino", 1, "1a Fase", 3));

        List<OpenMatchDay> kept = RecentMatchDays.limit(open, 2);

        assertThat(kept).containsExactly(open.get(1), open.get(2), open.get(3), open.get(4));
    }

    @Test
    void aDifferentPhaseOrGroupNumberIsAnotherGroup() {
        List<OpenMatchDay> open = List.of(day("tercera-masculino", 1, "1a Fase", 1),
                day("tercera-masculino", 1, "2a Fase", 2), day("tercera-masculino", 2, "1a Fase", 3),
                day("tercera-masculino", null, null, 4));

        assertThat(RecentMatchDays.limit(open, 1)).containsExactlyElementsOf(open);
    }

    @Test
    void fewerDaysThanTheLimitAreAllKept() {
        List<OpenMatchDay> open = List.of(day("tercera-masculino", 1, "1a Fase", 4),
                day("tercera-masculino", 1, "1a Fase", 5));

        assertThat(RecentMatchDays.limit(open, 3)).containsExactlyElementsOf(open);
    }

    @Test
    void theInputOrderOfTheKeptDaysIsPreserved() {
        List<OpenMatchDay> open = List.of(day("tercera-masculino", 1, "1a Fase", 5),
                day("tercera-masculino", 1, "1a Fase", 2), day("tercera-masculino", 1, "1a Fase", 4),
                day("tercera-masculino", 1, "1a Fase", 3));

        assertThat(rounds(RecentMatchDays.limit(open, 3))).containsExactly(5, 4, 3);
    }

    @Test
    void anEmptyListStaysEmpty() {
        assertThat(RecentMatchDays.limit(List.of(), 3)).isEmpty();
    }

    @Test
    void aLimitBelowOneIsRejected() {
        assertThatThrownBy(() -> RecentMatchDays.limit(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("perGroup must be at least 1");
    }
}
