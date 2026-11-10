package org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportExecutionIssue;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable tally kept for the duration of one fctt.cat traversal, shared by the legacy navigators and
 * the {@link FcttWebActasDirectoryNavigator} so a run that mixes layouts reports one set of counts.
 *
 * <p>Counts are kept at file and fixture level. A source whose files hold one fixture (FCTT, and the
 * ingest layout of BCNESA) simply never reports unresolved fixtures.</p>
 */
public final class FcttWebTraversalCounters {

    private long total;
    private long filesSeen;
    private long filesSkipped;
    private long fixturesSeen;
    private long fixturesDispatched;
    private long fixturesUnresolved;
    private long processorFailures;
    private final List<ImportExecutionIssue> issues = new ArrayList<>();

    public void total(long total) {
        this.total = total;
    }

    public void fileSeen() {
        filesSeen++;
    }

    public void fileSkipped() {
        filesSkipped++;
    }

    /** Counts {@code count} files of a folder that is skipped as a whole. */
    public void filesSeenAndSkipped(long count) {
        filesSeen += count;
        filesSkipped += count;
    }

    public void fixtureSeen() {
        fixturesSeen++;
    }

    public void fixtureDispatched() {
        fixturesDispatched++;
    }

    public void fixtureUnresolved() {
        fixturesUnresolved++;
    }

    public void processorFailed() {
        processorFailures++;
    }

    public void issue(ImportExecutionIssue issue) {
        issues.add(issue);
    }

    public long total() {
        return total;
    }

    public long filesSeen() {
        return filesSeen;
    }

    public long filesSkipped() {
        return filesSkipped;
    }

    public long fixturesSeen() {
        return fixturesSeen;
    }

    public long fixturesDispatched() {
        return fixturesDispatched;
    }

    public long fixturesUnresolved() {
        return fixturesUnresolved;
    }

    public long processorFailures() {
        return processorFailures;
    }

    public List<ImportExecutionIssue> issues() {
        return issues;
    }

    /** File-level progress: skipped covers skipped files and unresolved fixtures alike. */
    public ImportRunProgress progress() {
        return ImportRunProgress.determinate(filesSeen, total, filesSkipped + fixturesUnresolved,
                processorFailures);
    }
}
