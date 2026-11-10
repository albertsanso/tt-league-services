package org.cttelsamicsterrassa.data.load.shared.fcttweb.traverse;

import org.cttelsamicsterrassa.data.load.shared.execution.ImportRunContext;
import org.cttelsamicsterrassa.data.load.shared.fcttweb.process.FcttWebFixtureContext;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;
import org.cttelsamicsterrassa.data.load.shared.parse.acta.ActaGame;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds the context type of one source from what the {@link FcttWebActasDirectoryNavigator} found,
 * so the shared traversal can serve navigators whose processors are typed per source.
 */
@FunctionalInterface
public interface FcttWebContextFactory<C extends FcttWebFixtureContext> {

    /**
     * One fixture as the navigator found it.
     *
     * @param season      season folder, in {@code YYYY-YYYY} form
     * @param competition stored league competition, before gender is folded in (FCTT) or after the
     *                    BCNESA folder mapping
     * @param gender      {@code male} or {@code female} for FCTT, {@code null} for BCNESA
     * @param group       group folder, or {@code null} when the layout has none
     * @param phase       phase folder (BCNESA); FCTT takes the phase from the payload
     * @param round       match day from the payload
     * @param fixtureIndex 0-based position within the file
     * @param homeTeamName home club, or {@code null} when it could not be attributed
     * @param awayTeamName away club, or {@code null} when it could not be attributed
     * @param games       this fixture's own games, a sub-list of {@code acta.games()}
     */
    record Report(String season, String competition, String gender, String group, String phase, int round,
                  int fixtureIndex, String homeTeamName, String awayTeamName, Path file, Acta acta,
                  List<ActaGame> games, ImportRunContext runContext) {
    }

    C create(Report report);
}
