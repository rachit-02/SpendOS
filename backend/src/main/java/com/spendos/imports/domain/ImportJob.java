package com.spendos.imports.domain;

import com.spendos.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob extends CreatedEntity {

    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "account_id")
    private UUID accountId;

    /** SHA-256 of the file content, used to stop the same file being imported twice. */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /** Data rows detected in the file; denominator of the progress percentage. */
    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "import_status", nullable = false, length = 50)
    private String importStatus = PENDING;

    @Column(name = "total_rows_processed")
    private int totalRowsProcessed;

    @Column(name = "imported_count")
    private int importedCount;

    @Column(name = "duplicate_count")
    private int duplicateCount;

    @Column(name = "invalid_count")
    private int invalidCount;

    /** JSON summary: error counts by code plus the detected file format. */
    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
