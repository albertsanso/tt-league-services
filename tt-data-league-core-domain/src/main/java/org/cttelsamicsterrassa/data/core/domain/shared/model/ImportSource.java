package org.cttelsamicsterrassa.data.core.domain.shared.model;

/**
 * The federation an imported row came from.
 *
 * <p>RFETM and BCNESA number player licences and name clubs independently, so a licence or a club
 * name from one federation carries no identity guarantee against the other: measured across both
 * exports, 212 licences appear in both, and all 212 belong to different people. {@link Club} and
 * {@link org.cttelsamicsterrassa.data.core.domain.player.model.PlayerSeason} therefore key their
 * federation-scoped natural lookups on {@code (source, ...)} rather than on the raw value alone.</p>
 */
public enum ImportSource {
    RFETM,
    BCNESA
}
