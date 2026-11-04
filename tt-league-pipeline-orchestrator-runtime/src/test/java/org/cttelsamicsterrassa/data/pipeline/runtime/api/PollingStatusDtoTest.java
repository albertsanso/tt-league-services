package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.api.PollingStatusDto.SourceModeDto;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Polling;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule.SourceSchedule;
import org.junit.jupiter.api.Test;

class PollingStatusDtoTest {

    private static Polling polling(PipelineSource... adaptive) {
        return new Polling(adaptive.length == 0 ? null : Set.of(adaptive), Duration.ofMinutes(2), null, null, null,
                null);
    }

    @Test
    void everySourceIsListedInEnumOrderWithItsMode() {
        Schedule schedule = new Schedule("2026-2027", "Europe/Madrid", Duration.ofMinutes(10), Duration.ofSeconds(30),
                Map.of(PipelineSource.RFETM, new SourceSchedule("0 0 3 * * *")));

        PollingStatusDto status = PollingStatusDto.from(schedule, polling(PipelineSource.FCTT));

        assertThat(status.season()).isEqualTo("2026-2027");
        assertThat(status.zone()).isEqualTo("Europe/Madrid");
        assertThat(status.tickInterval()).isEqualTo(Duration.ofMinutes(2));
        assertThat(status.sources()).containsExactly(
                new SourceModeDto(PipelineSource.RFETM, "CRON", "0 0 3 * * *"),
                new SourceModeDto(PipelineSource.BCNESA, "NONE", null),
                new SourceModeDto(PipelineSource.FCTT, "ADAPTIVE", null));
    }

    @Test
    void seasonAndZoneAreNullWhenNothingIsScheduled() {
        PollingStatusDto status = PollingStatusDto.from(
                new Schedule(null, null, null, null, Map.of()), polling());

        assertThat(status.season()).isNull();
        assertThat(status.zone()).isNull();
        assertThat(status.sources()).extracting(SourceModeDto::mode).containsOnly("NONE");
    }

    @Test
    void anAdaptiveSourceAloneMakesTheScheduleSeasonAndZoneVisible() {
        PollingStatusDto status = PollingStatusDto.from(
                new Schedule("2026-2027", "Europe/Madrid", null, null, Map.of()), polling(PipelineSource.BCNESA));

        assertThat(status.season()).isEqualTo("2026-2027");
        assertThat(status.zone()).isEqualTo("Europe/Madrid");
        assertThat(status.sources()).extracting(SourceModeDto::mode).containsExactly("NONE", "ADAPTIVE", "NONE");
    }
}
