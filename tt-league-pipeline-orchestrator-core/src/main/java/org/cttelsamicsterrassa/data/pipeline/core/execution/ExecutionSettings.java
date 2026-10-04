package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.util.Objects;

public record ExecutionSettings(RetryPolicy retry, StepTimeouts timeouts, PollIntervals polls) {

    public ExecutionSettings {
        Objects.requireNonNull(retry, "retry is required");
        Objects.requireNonNull(timeouts, "timeouts is required");
        Objects.requireNonNull(polls, "polls is required");
    }
}
