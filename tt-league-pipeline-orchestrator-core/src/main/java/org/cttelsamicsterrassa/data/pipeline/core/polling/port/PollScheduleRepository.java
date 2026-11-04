package org.cttelsamicsterrassa.data.pipeline.core.polling.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** Persistence port of the poll schedules. */
public interface PollScheduleRepository {

    /** Every schedule of the source and season, the {@code FULL_REFRESH} unit included. */
    List<PollSchedule> findBySourceAndSeason(PipelineSource source, String season);

    Optional<PollSchedule> findById(UUID id);

    /**
     * Inserts a schedule that does not exist, or updates one whose stored version equals {@code schedule.version()},
     * and returns it with the stored version. Throws {@link StalePollScheduleException} on a version conflict, when the
     * stored row was removed, or when another writer created the same {@code (source, season, scopeKey)}.
     */
    PollSchedule save(PollSchedule schedule);

    void delete(UUID id);

    /** Schedules ordered by source, season and scope key; a null argument does not filter. */
    List<PollSchedule> query(PipelineSource source, String season);
}
