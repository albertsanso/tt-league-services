package org.cttelsamicsterrassa.data.core.repository.jpa.match.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * The manual overdue mark of a match (FEAT-00092). One row per match at most: {@code match_id} is
 * both the primary key and a foreign key to {@code match_record(id)}. There is deliberately no
 * cascade from {@code match_record}, and no inverse mapping on {@link MatchJPA}; the mark keeps the
 * operator's decision separate from import data, so import writes never touch it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "match_overdue_mark")
public class MatchOverdueMarkJPA {

    @Id
    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_match_overdue_mark_match"))
    private MatchJPA match;

    @Column(name = "marked_at", nullable = false)
    private ZonedDateTime markedAt;

    @Column(name = "marked_by", nullable = false, length = 255)
    private String markedBy;

    public MatchOverdueMarkJPA(MatchJPA match, ZonedDateTime markedAt, String markedBy) {
        this.match = match;
        this.markedAt = markedAt;
        this.markedBy = markedBy;
    }
}