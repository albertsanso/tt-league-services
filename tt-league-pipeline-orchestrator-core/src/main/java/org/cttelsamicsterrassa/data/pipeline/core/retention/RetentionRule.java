package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.time.Duration;

/** When the artifacts of one kind expire. */
public sealed interface RetentionRule {

    /** Expires an artifact once its age reaches the duration. */
    record MaxAge(Duration duration) implements RetentionRule {
        public MaxAge {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("max-age must be a positive duration");
            }
        }
    }

    /** Keeps the artifacts of the newest N seasons for which the source has runs. */
    record Seasons(int count) implements RetentionRule {
        public Seasons {
            if (count < 1 || count > 10) {
                throw new IllegalArgumentException("seasons must be between 1 and 10");
            }
        }
    }
}
