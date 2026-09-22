package com.spendos.imports.service;

import com.spendos.common.exception.ApiException;
import com.spendos.common.util.PageRequests;
import com.spendos.common.util.Times;
import com.spendos.imports.domain.ImportJob;
import com.spendos.imports.dto.ImportDtos.ImportErrorResponse;
import com.spendos.imports.dto.ImportDtos.ImportJobResponse;
import com.spendos.imports.dto.ImportDtos.ImportStatusResponse;
import com.spendos.imports.dto.ImportDtos.UploadResponse;
import com.spendos.imports.parser.CsvStatementParser;
import com.spendos.imports.parser.PdfStatementParser;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import com.spendos.imports.repository.ImportErrorRepository;
import com.spendos.imports.repository.ImportJobRepository;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.service.AccountService;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "txt", "pdf");
    // Windows browsers report .csv as application/vnd.ms-excel; octet-stream comes from some clients.
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("text/csv", "text/plain", "application/csv",
            "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream",
            "application/pdf", "application/x-pdf");
    private static final Map<String, String> SORT_FIELDS = Map.of("createdAt", "createdAt", "fileName", "fileName");

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final AccountService accountService;
    private final ImportProcessor processor;
    private final LocalDateTime startedAt = Times.nowUtc();

    public ImportService(ImportJobRepository jobRepository, ImportErrorRepository errorRepository,
                         AccountService accountService, ImportProcessor processor) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
        this.accountService = accountService;
        this.processor = processor;
    }

    /**
     * Validates the file synchronously (type, size, structure) and queues row processing.
     * Not transactional: the job row must be committed before the background worker reads it.
     */
    public UploadResponse upload(UUID userId, MultipartFile file, UUID accountId, String dateFormat) {
        byte[] bytes = readAndValidate(file);
        Account account = resolveAccount(userId, accountId);
        String hash = sha256(bytes);
        jobRepository.findByUserIdAndFileHashAndImportStatusIn(userId, hash,
                        List.of(ImportJob.PENDING, ImportJob.PROCESSING, ImportJob.COMPLETED))
                .stream().findFirst().ifPresent(previous -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_IMPORT",
                            "This file has already been imported", Map.of("importJobId", previous.getId()));
                });
        // The format is decided by the file's content, not its name: users should not need to care.
        ParsedFile parsed = PdfStatementParser.isPdf(bytes) ? PdfStatementParser.parse(bytes) : CsvStatementParser.parse(bytes);

        ImportJob job = new ImportJob();
        job.setUserId(userId);
        job.setAccountId(account.getId());
        job.setFileName(safeFileName(file.getOriginalFilename()));
        job.setFileSizeBytes((long) bytes.length);
        job.setFileHash(hash);
        job.setTotalRows(parsed.rows().size());
        job.setImportStatus(ImportJob.PENDING);
        job = jobRepository.save(job);

        processor.processAsync(job.getId(), userId, account.getId(), parsed, dateFormat);
        log.info("Import queued | jobId={} | userId={} | rows={}", job.getId(), userId, parsed.rows().size());
        return new UploadResponse(job.getId(), ImportJob.PROCESSING,
                "Import processing started. Check status with GET /imports/" + job.getId() + "/status");
    }

    @Transactional(readOnly = true)
    public Page<ImportJobResponse> history(UUID userId, int page, int pageSize, String sortBy, String sortOrder) {
        String field = SORT_FIELDS.getOrDefault(sortBy == null ? "createdAt" : sortBy, "createdAt");
        Sort sort = Sort.by(PageRequests.direction(sortOrder), field);
        return jobRepository.findByUserId(userId, PageRequests.of(page, pageSize, sort)).map(ImportJobResponse::from);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse get(UUID userId, UUID jobId) {
        return ImportJobResponse.from(require(userId, jobId));
    }

    @Transactional(readOnly = true)
    public ImportStatusResponse status(UUID userId, UUID jobId) {
        return ImportStatusResponse.from(require(userId, jobId));
    }

    @Transactional(readOnly = true)
    public Page<ImportErrorResponse> errors(UUID userId, UUID jobId, int page, int pageSize) {
        require(userId, jobId);
        return errorRepository.findByImportJobIdOrderByRowNumberAsc(jobId, PageRequests.of(page, pageSize, Sort.unsorted()))
                .map(ImportErrorResponse::from);
    }

    /** Jobs left pending/processing by a previous run of the server can never finish; mark them failed. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedJobs() {
        List<ImportJob> stuck = jobRepository.findByImportStatusInAndCreatedAtBefore(
                List.of(ImportJob.PENDING, ImportJob.PROCESSING), startedAt);
        stuck.forEach(job -> {
            job.setImportStatus(ImportJob.FAILED);
            job.setCompletedAt(Times.nowUtc());
            job.setErrorSummary("{\"error\":\"Import was interrupted by a server restart. Please upload the file again.\"}");
        });
        if (!stuck.isEmpty()) {
            log.warn("Marked interrupted imports as failed | count={}", stuck.size());
        }
    }

    private ImportJob require(UUID userId, UUID jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ApiException.notFound("Import job not found"));
    }

    private Account resolveAccount(UUID userId, UUID accountId) {
        if (accountId == null) {
            return accountService.primaryOrDefault(userId);
        }
        try {
            return accountService.require(userId, accountId);
        } catch (ApiException notFound) {
            throw ApiException.badRequest("INVALID_REQUEST", "Invalid account ID");
        }
    }

    static byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "The file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File too large (max 50MB)");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "File must be a CSV or PDF bank statement (.csv or .pdf)");
        }
        String contentType = file.getContentType() == null ? null
                : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "File must be a CSV or PDF bank statement");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("INVALID_FILE_TYPE", "Could not read the uploaded file");
        }
        if (PdfStatementParser.isPdf(bytes)) {
            return bytes; // PDFs are binary by nature; the PDF parser validates them
        }
        if (extension.equals("pdf")) {
            throw ApiException.badRequest("INVALID_PDF", "This file has a .pdf name but is not a PDF. "
                    + "Download the statement from your bank again.");
        }
        for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
            if (bytes[i] == 0) {
                throw ApiException.badRequest("INVALID_FILE_TYPE", "File appears to be binary, not CSV text");
            }
        }
        return bytes;
    }

    static String safeFileName(String original) {
        String name = original == null ? "statement.csv" : Paths.get(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}<>\"]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
