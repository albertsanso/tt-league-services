package org.cttelsamicsterrassa.data.core.repository.jpa.load.model;

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
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "import_job_season",
        indexes = @Index(name = "idx_import_job_season_job_id", columnList = "import_job_id")
)
public class ImportJobSeasonJPA {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJobJPA job;

    @Column(name = "season_index", nullable = false)
    private int position;

    @Column(name = "season", nullable = false)
    private String season;

    /**
     * Intentionally a plain column and not a {@code @JoinColumn} to {@code ImportResourceJPA}: a season row is a
     * snapshot of what the job ran and must outlive changes to the import resource.
     */
    @Column(name = "import_resource_id", nullable = false)
    private UUID importResourceId;

    /** The run id of the in-memory import run registry; it has no table to reference. */
    @Column(name = "import_run_id")
    private UUID importRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportRunStatus status;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    /** The season's {@code ImportProcessResult} as JSON, written by {@code ImportProcessResultJsonCodec}. */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;
}
