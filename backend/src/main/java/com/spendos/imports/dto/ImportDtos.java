package com.spendos.imports.dto;

import com.spendos.common.util.Times;
import com.spendos.imports.domain.ImportError;
import com.spendos.imports.domain.ImportJob;
import java.time.Instant;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {
    }

    public record UploadResponse(UUID importJobId, String status, String message) {
    }

    public record ImportJobResponse(UUID id, String fileName, Long fileSizeBytes, UUID accountId, String importStatus,
                                    int importedCount, int duplicateCount, int invalidCount, int totalRowsProcessed,
                                    int totalRows, String errorSummary, Instant createdAt, Instant startedAt,
                                    Instant completedAt) {
        public static ImportJobResponse from(ImportJob job) {
            return new ImportJobResponse(job.getId(), job.getFileName(), job.getFileSizeBytes(), job.getAccountId(),
                    job.getImportStatus(), job.getImportedCount(), job.getDuplicateCount(), job.getInvalidCount(),
                    job.getTotalRowsProcessed(), job.getTotalRows(), job.getErrorSummary(),
                    Times.utc(job.getCreatedAt()), Times.utc(job.getStartedAt()), Times.utc(job.getCompletedAt()));
        }
    }

    public record ImportStatusResponse(UUID importJobId, String status, Progress progress) {
        public static ImportStatusResponse from(ImportJob job) {
            int total = job.getTotalRows();
            int processed = Math.min(job.getTotalRowsProcessed(), Math.max(total, job.getTotalRowsProcessed()));
            int percentage = ImportJob.COMPLETED.equals(job.getImportStatus()) ? 100
                    : total == 0 ? 0 : (int) Math.min(99, Math.floor(processed * 100.0 / total));
            return new ImportStatusResponse(job.getId(), job.getImportStatus(), new Progress(processed, total, percentage));
        }
    }

    public record Progress(int processed, int total, int percentage) {
    }

    public record ImportErrorResponse(UUID id, int rowNumber, String rawData, String errorMessage, String errorCode) {
        public static ImportErrorResponse from(ImportError error) {
            return new ImportErrorResponse(error.getId(), error.getRowNumber(), error.getRawData(),
                    error.getErrorMessage(), error.getErrorCode());
        }
    }
}
