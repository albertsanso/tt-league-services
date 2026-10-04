package org.cttelsamicsterrassa.data.core.repository.jpa.load.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cttelsamicsterrassa.data.core.domain.load.job.ImportJobStatus;
import org.cttelsamicsterrassa.data.core.domain.resource.model.UploadMode;
import org.cttelsamicsterrassa.data.core.repository.jpa.common.Source;

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
        name = "import_job",
        indexes = {
                @Index(name = "idx_import_job_source_sha", columnList = "source, content_sha256"),
                @Index(name = "idx_import_job_created", columnList = "created_at"),
                @Index(name = "idx_import_job_status", columnList = "status")
        }
)
public class ImportJobJPA {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;

    /** The manifest seasons, comma-separated in manifest order. */
    @Column(name = "seasons", nullable = false, columnDefinition = "TEXT")
    private String seasons;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_mode", nullable = false)
    private UploadMode mode;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "client_run_id", length = 64)
    private String clientRunId;

    @Column(name = "manifest_run_id", length = 64)
    private String manifestRunId;

    @Column(name = "allow_published_shrink", nullable = false)
    private boolean allowPublishedShrink;

    @Column(name = "staged_zip_path", nullable = false, length = 1024)
    private String stagedZipPath;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportJobStatus status;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ImportJobSeasonJPA> seasonResults = new ArrayList<>();
}
