package org.cttelsamicsterrassa.data.pipeline.core.run;

/** Kind of work a pipeline step performs. */
public enum StepKind {
    /** The ingest run: download, parse and package. */
    INGEST,
    /** Upload ZIP download and SHA-256 check. */
    FETCH_PACKAGE,
    /** Platform import job. */
    IMPORT
}
