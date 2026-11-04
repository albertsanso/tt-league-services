package org.cttelsamicsterrassa.data.pipeline.core.statistics;

/** How long a match has been waiting for its result, measured from its match date (lower bounds inclusive). */
public enum AgeBucket {
    UNDER_1_DAY,
    DAYS_1_TO_2,
    DAYS_2_TO_7,
    OVER_7_DAYS
}
