-- Fixed-schedule trigger (FEAT-00106): ShedLock JDBC lock table, one row per lock name (pipeline-schedule-<SOURCE>).
-- Times are written with the database clock (ShedLock usingDbTime), so every instance compares the same clock.

CREATE TABLE pipeline.shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp(3) NOT NULL,
    locked_at  timestamp(3) NOT NULL,
    locked_by  varchar(255) NOT NULL
);
