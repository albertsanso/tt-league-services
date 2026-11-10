package org.cttelsamicsterrassa.data.load.shared.fcttweb.process;

import org.cttelsamicsterrassa.data.load.shared.parse.acta.Acta;

/**
 * Hook that brings a played acta to home/away orientation before it is stored.
 *
 * <p>The current ingest layout is always home/away, so {@link #IDENTITY} applies. Older FCTT
 * extracts key lineups, doubles and games by the A/B/C column instead and need
 * {@link MirroredActaOrientation}.</p>
 */
@FunctionalInterface
public interface ActaOrientation {

    /** The payload is already home/away. */
    ActaOrientation IDENTITY = acta -> acta;

    /** Legacy FCTT extracts whose lineups, doubles and games may be mirrored. */
    ActaOrientation MIRRORED = MirroredActaOrientation::toHomeAway;

    Acta toHomeAway(Acta acta);

    /** The orientation a source needs by default. */
    static ActaOrientation defaultFor(org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource source) {
        return source == org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource.FCTT ? MIRRORED : IDENTITY;
    }
}
