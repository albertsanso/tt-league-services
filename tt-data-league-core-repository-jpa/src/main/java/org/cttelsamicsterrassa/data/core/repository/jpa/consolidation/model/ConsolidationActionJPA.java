package org.cttelsamicsterrassa.data.core.repository.jpa.consolidation.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.domain.consolidation.model.ConsolidationActionType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "consolidation_action",
        indexes = @Index(name = "idx_consolidation_action_occurred_on", columnList = "occurred_on")
)
public class ConsolidationActionJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ConsolidationActionType type;

    @Column(name = "occurred_on", nullable = false)
    private ZonedDateTime occurredOn;

    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @Column(name = "performed_by_username", length = 255)
    private String performedByUsername;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @OneToMany(mappedBy = "action", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsolidationActionClubJPA> clubs = new ArrayList<>();
}
