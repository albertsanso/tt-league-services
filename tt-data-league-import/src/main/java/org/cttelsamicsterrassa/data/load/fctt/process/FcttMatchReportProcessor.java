package org.cttelsamicsterrassa.data.load.fctt.process;

/**
 * Processes one FCTT match report.
 *
 * <p>Implementations are source-specific and should use the directory context and payload identity
 * rules supplied by {@link FcttMatchReportContext}. A processor failure is isolated by the navigator,
 * so implementations should be idempotent.</p>
 */
@FunctionalInterface
public interface FcttMatchReportProcessor {

    void process(FcttMatchReportContext context);
}
