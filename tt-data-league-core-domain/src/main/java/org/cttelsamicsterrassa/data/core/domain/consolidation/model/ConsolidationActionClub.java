package org.cttelsamicsterrassa.data.core.domain.consolidation.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An immutable snapshot of a club as it existed at the moment a consolidation ran.
 *
 * <p>This is deliberately a copied value rather than a reference to
 * {@code org.cttelsamicsterrassa.data.core.domain.club.model.Club}: a merge deletes every
 * non-primary club in the same operation, so most recorded ids no longer resolve. Never resolve
 * {@link #getClubId()} back through {@code ClubRepository} when reading history.</p>
 */
public final class ConsolidationActionClub {
    private final UUID clubId;
    private final String clubName;

    private ConsolidationActionClub(UUID clubId, String clubName) {
        this.clubId = Objects.requireNonNull(clubId, "clubId must not be null");
        Objects.requireNonNull(clubName, "clubName must not be null");
        if (clubName.isBlank()) {
            throw new IllegalArgumentException("clubName must not be blank");
        }
        this.clubName = clubName;
    }

    public static ConsolidationActionClub of(UUID clubId, String clubName) {
        return new ConsolidationActionClub(clubId, clubName);
    }

    public UUID getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConsolidationActionClub that)) {
            return false;
        }
        return clubId.equals(that.clubId) && clubName.equals(that.clubName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clubId, clubName);
    }

    @Override
    public String toString() {
        return "ConsolidationActionClub{clubId=" + clubId + ", clubName='" + clubName + "'}";
    }
}
