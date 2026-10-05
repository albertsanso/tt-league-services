package org.cttelsamicsterrassa.data.pipeline.core.run;

/** Source failures an ingest run reported, summed over its stages (FEAT-00113). */
public record IngestHealth(long httpErrors, long timeouts, long parseErrors) {

    public IngestHealth {
        Checks.nonNegative(httpErrors, "httpErrors");
        Checks.nonNegative(timeouts, "timeouts");
        Checks.nonNegative(parseErrors, "parseErrors");
    }
}
