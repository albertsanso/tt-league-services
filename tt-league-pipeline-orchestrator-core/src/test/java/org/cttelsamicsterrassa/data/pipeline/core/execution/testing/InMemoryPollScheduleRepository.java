package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Version-checked in-memory schedule store with the unique {@code (source, season, scopeKey)} rule. */
public class InMemoryPollScheduleRepository implements PollScheduleRepository {

    private final Map<UUID, PollSchedule> schedules = new LinkedHashMap<>();
    private int saveCalls;

    public synchronized int saveCalls() {
        return saveCalls;
    }

    public synchronized List<PollSchedule> all() {
        return List.copyOf(schedules.values());
    }

    @Override
    public synchronized List<PollSchedule> findBySourceAndSeason(PipelineSource source, String season) {
        return query(source, season);
    }

    @Override
    public synchronized Optional<PollSchedule> findById(UUID id) {
        return Optional.ofNullable(schedules.get(id));
    }

    @Override
    public synchronized PollSchedule save(PollSchedule schedule) {
        saveCalls++;
        PollSchedule stored = schedules.get(schedule.id());
        if (stored == null) {
            if (schedule.version() != 0L) {
                throw new StalePollScheduleException(schedule.id(), "Schedule " + schedule.id() + " was removed");
            }
            boolean duplicate = schedules.values().stream().anyMatch(other -> other.source() == schedule.source()
                    && other.season().equals(schedule.season()) && other.scopeKey().equals(schedule.scopeKey()));
            if (duplicate) {
                throw new StalePollScheduleException(schedule.id(), "Schedule already exists for the same unit");
            }
            schedules.put(schedule.id(), schedule);
            return schedule;
        }
        if (stored.version() != schedule.version()) {
            throw new StalePollScheduleException(schedule.id(), "Schedule " + schedule.id() + " changed concurrently");
        }
        PollSchedule next = PollSchedule.restore(schedule.id(), schedule.source(), schedule.season(),
                schedule.scopeKey(), schedule.filter(), schedule.level(), schedule.interval(),
                schedule.consecutiveNoChange(), schedule.nextRunAt(), schedule.lastRunAt(), schedule.pendingRunId(),
                schedule.stoppedAt(), schedule.stopReason(), schedule.alertedAt(), schedule.version() + 1);
        schedules.put(schedule.id(), next);
        return next;
    }

    @Override
    public synchronized void delete(UUID id) {
        schedules.remove(id);
    }

    @Override
    public synchronized List<PollSchedule> query(PipelineSource source, String season) {
        return schedules.values().stream()
                .filter(schedule -> source == null || schedule.source() == source)
                .filter(schedule -> season == null || schedule.season().equals(season))
                .sorted(Comparator.comparing(PollSchedule::source).thenComparing(PollSchedule::season)
                        .thenComparing(PollSchedule::scopeKey))
                .toList();
    }
}
