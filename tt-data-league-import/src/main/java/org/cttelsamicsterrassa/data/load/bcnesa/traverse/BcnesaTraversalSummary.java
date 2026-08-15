package org.cttelsamicsterrassa.data.load.bcnesa.traverse;

/**
 * What one BCNESA traversal did.
 *
 * <p>A BCNESA file holds a whole matchday and splits into several fixtures, so counts are kept at
 * both levels: {@code filesSeen}/{@code filesSkipped} describe the walk over the {@code acta*.json}
 * files, {@code fixturesSeen}/{@code fixturesDispatched}/{@code fixturesUnresolved} describe what came
 * out of splitting them.</p>
 *
 * @param filesSeen          match report files encountered under the base folder
 * @param filesSkipped       files skipped because the payload could not be parsed or carried no
 *                           match day
 * @param fixturesSeen       fixtures produced by splitting the files that were read
 * @param fixturesDispatched fixtures whose context reached at least one processor
 * @param fixturesUnresolved fixtures skipped because their clubs could not be attributed
 * @param processorFailures  individual processor invocations that threw
 */
public record BcnesaTraversalSummary(long filesSeen,
                                     long filesSkipped,
                                     long fixturesSeen,
                                     long fixturesDispatched,
                                     long fixturesUnresolved,
                                     long processorFailures) {

    @Override
    public String toString() {
        return "%d files seen, %d files skipped, %d fixtures seen, %d dispatched, %d unresolved, %d processor failures"
                .formatted(filesSeen, filesSkipped, fixturesSeen, fixturesDispatched, fixturesUnresolved,
                        processorFailures);
    }
}
