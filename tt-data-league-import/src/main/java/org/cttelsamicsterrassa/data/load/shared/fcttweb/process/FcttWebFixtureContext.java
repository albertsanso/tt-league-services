package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.core.domain.shared.model.Season;
import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;

import java.nio.file.Path;
import java.util.List;

/**
 * What the shared fctt.cat processors need to know about one fixture, whichever source it comes
 * from. Both {@code BcnesaMatchReportContext} and {@code FcttMatchReportContext} implement it, so
 * one processor set serves both sources while each source keeps its own public context type.
 *
 * <p>A context describes exactly one fixture: {@link #games()} is that fixture's own slice of the
 * payload and {@link #homeTeamName()} / {@link #awayTeamName()} are its clubs, {@code null} when
 * they could not be attributed. Report file names are never parsed; {@link #round()} comes from the
 * payload.</p>
 */
public interface FcttWebFixtureContext {

    /** Season folder, in {@code YYYY-YYYY} form. */
    String season();

    /** The season folder as a domain {@link Season}. */
    Season toSeason();

    /** Stored competition identity (the folder, with gender folded in for FCTT). */
    String competition();

    /** Group folder, or {@code null} when the layout has none. */
    String group();

    /** Stored group number, or {@code null} when the fixture carries no numbered group. */
    Integer groupNumberOrNull();

    int round();

    /** The match phase, or {@code null} when absent. */
    String phase();

    /** 0-based position of this fixture within its file; every file holds one fixture except legacy BCNESA ones. */
    default int fixtureIndex() {
        return 0;
    }

    String homeTeamName();

    String awayTeamName();

    Path matchReportFile();

    Acta acta();

    /** This fixture's own games, a sub-list of {@code acta().games()}. */
    List<ActaGame> games();

    ImportRunContext runContext();

    /** Whether both clubs of this fixture were attributed. */
    default boolean isResolved() {
        return homeTeamName() != null && awayTeamName() != null;
    }

    /** Whether a group folder exists but is not a group number (FCTT layouts only). */
    default boolean hasInvalidGroupFolder() {
        return false;
    }

    /** {@code male} / {@code female} folder gender (FCTT layouts only), otherwise {@code null}. */
    default String gender() {
        return null;
    }

    /** {@code masculino} / {@code femenino} derived from {@link #gender()}, otherwise {@code null}. */
    default String sex() {
        return null;
    }

    /** The same fixture carrying {@code acta}, used when an {@link ActaOrientation} rewrote the payload. */
    FcttWebFixtureContext withActa(Acta acta);
}
