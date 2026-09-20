package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionClubRole;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "consolidation_action_club",
        indexes = @Index(name = "idx_consolidation_action_club_club_id", columnList = "club_id")
)
public class ConsolidationActionClubJPA {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consolidation_action_id", nullable = false)
    private ConsolidationActionJPA action;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ConsolidationActionClubRole role;

    /**
     * Intentionally a plain column and <strong>not</strong> a {@code @JoinColumn} to
     * {@code ClubJPA}: a merge deletes the source club rows in the same transaction, so a foreign
     * key would either reject the insert or cascade away the very audit record this table exists to
     * keep. Do not "fix" this into an association.
     */
    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "club_name", nullable = false, length = 255)
    private String clubName;
}
