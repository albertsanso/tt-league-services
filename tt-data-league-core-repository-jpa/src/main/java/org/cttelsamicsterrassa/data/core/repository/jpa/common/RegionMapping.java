package org.cttelsamicsterrassa.data.core.repository.jpa.common;

/**
 * Converts between the domain {@code Region}/{@code RegionType} and their JPA mirrors by constant name. A
 * constant added to only one side fails here with an {@link IllegalArgumentException}, in one place.
 */
public final class RegionMapping {

    private RegionMapping() {
    }

    public static Region toJpa(org.cttelsamicsterrassa.data.core.domain.shared.model.Region region) {
        return region == null ? null : Region.valueOf(region.name());
    }

    public static RegionType toJpa(org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType regionType) {
        return regionType == null ? null : RegionType.valueOf(regionType.name());
    }

    public static org.cttelsamicsterrassa.data.core.domain.shared.model.Region toDomain(Region region) {
        return region == null ? null
                : org.cttelsamicsterrassa.data.core.domain.shared.model.Region.valueOf(region.name());
    }

    public static org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType toDomain(RegionType regionType) {
        return regionType == null ? null
                : org.cttelsamicsterrassa.data.core.domain.shared.model.RegionType.valueOf(regionType.name());
    }
}
