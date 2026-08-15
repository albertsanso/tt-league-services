package org.cttelsamicsterrassa.data.core.domain.shared.model;

/**
 * The federation an imported row came from.
 *
 * <p>Federations number player licences and name clubs independently, so a licence or club name
 * from one source carries no identity guarantee in another. Club and
 * {@link org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason} lookups therefore
 * include {@code source} rather than using the raw value alone.</p>
 */
public enum ImportSource {
    RFETM,
    BCNESA,
    FCTT
}
