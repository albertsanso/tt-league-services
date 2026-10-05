package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** One condition that currently holds. {@code source} and {@code season} are absent for kinds that have none. */
public record AlertCondition(
        AlertKind kind, String conditionKey, PipelineSource source, String season, String title, String detail) {

    public AlertCondition {
        AlertChecks.required(kind, "kind");
        AlertChecks.nonBlankMax(conditionKey, "conditionKey", 255);
        AlertChecks.nonBlankMax(title, "title", 255);
        AlertChecks.required(detail, "detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
    }
}
