package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** Round progress and calendar disagree (an import ran in between); nothing is written and the next recompute repairs it. */
public class TrackerInconsistencyException extends RuntimeException {

    public TrackerInconsistencyException(String message) {
        super(message);
    }
}
