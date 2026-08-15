package org.cttelsamicsterrassa.data.load.shared.process;

/**
 * Processes one match report.
 *
 * <p>Implementations are stateless Spring beans and are discovered by the navigator through
 * dependency injection; adding one never requires changing the traversal. When the order in which
 * processors run matters, express it with {@code @Order} — for example clubs before players before
 * matches.</p>
 *
 * <p>A processor that throws is isolated: the navigator logs the failure, counts it, and carries on
 * with the remaining processors and files. Implementations should therefore be idempotent, so that
 * re-running a season after fixing a failure is safe.</p>
 */
@FunctionalInterface
public interface MatchReportProcessor {

    void process(MatchReportContext context);
}
