package com.spendos.imports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.categories.engine.CategoryEngine;
import com.spendos.categories.engine.CategoryEngine.Categorization;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Times;
import com.spendos.imports.domain.ImportError;
import com.spendos.imports.domain.ImportJob;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import com.spendos.imports.parser.DateParser;
import com.spendos.imports.repository.ImportErrorRepository;
import com.spendos.imports.repository.ImportJobRepository;
import com.spendos.imports.validator.StatementRowValidator;
import com.spendos.imports.validator.StatementRowValidator.Candidate;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.domain.UserMerchantMapping;
import com.spendos.merchants.normalizer.MerchantNormalizer;
import com.spendos.merchants.repository.MerchantRepository;
import com.spendos.merchants.repository.UserMerchantMappingRepository;
import com.spendos.merchants.service.MerchantService;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.service.TransactionsChangedEvent;
import com.spendos.users.service.UserPreferencesService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the import pipeline for one job: validate rows, detect duplicates, normalize merchants,
 * categorize, and save in chunks while publishing progress. Each chunk commits independently so
 * progress is visible and a late failure keeps the rows already imported.
 */
@Component
public class ImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);
    static final int CHUNK_SIZE = 250;

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final UserMerchantMappingRepository mappingRepository;
    private final MerchantNormalizer merchantNormalizer;
    private final MerchantService merchantService;
    private final CategoryEngine categoryEngine;
    private final UserPreferencesService preferencesService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public ImportProcessor(ImportJobRepository jobRepository, ImportErrorRepository errorRepository,
                           TransactionRepository transactionRepository, MerchantRepository merchantRepository,
                           UserMerchantMappingRepository mappingRepository, MerchantNormalizer merchantNormalizer,
                           MerchantService merchantService, CategoryEngine categoryEngine,
                           UserPreferencesService preferencesService, TransactionTemplate transactionTemplate,
                           ApplicationEventPublisher events, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.mappingRepository = mappingRepository;
        this.merchantNormalizer = merchantNormalizer;
        this.merchantService = merchantService;
        this.categoryEngine = categoryEngine;
        this.preferencesService = preferencesService;
        this.transactionTemplate = transactionTemplate;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Async("importExecutor")
    public void processAsync(UUID jobId, UUID userId, UUID accountId, ParsedFile parsed, String dateFormat) {
        process(jobId, userId, accountId, parsed, dateFormat);
    }

    public void process(UUID jobId, UUID userId, UUID accountId, ParsedFile parsed, String dateFormat) {
        try {
            run(jobId, userId, accountId, parsed, dateFormat);
        } catch (ApiException exception) {
            fail(jobId, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Import failed | jobId={}", jobId, exception);
            fail(jobId, "Import failed unexpectedly. Rows imported before the failure were kept.");
        }
    }

    private void run(UUID jobId, UUID userId, UUID accountId, ParsedFile parsed, String dateFormat) {
        LocalDate today = LocalDate.now(preferencesService.zoneFor(userId));
        String currency = preferencesService.currencyFor(userId);
        updateJob(jobId, job -> {
            job.setImportStatus(ImportJob.PROCESSING);
            job.setStartedAt(Times.nowUtc());
            job.setTotalRows(parsed.rows().size());
        });

        StatementRowValidator.Result validation = StatementRowValidator.validate(
                parsed.rows(), parsed.mapping(), DateParser.orderFromHint(dateFormat), today);
        List<Candidate> candidates = validation.candidates();

        DuplicateDetector.Result dedupe = candidates.isEmpty()
                ? new DuplicateDetector.Result(List.of(), List.of())
                : DuplicateDetector.detect(candidates, existingInRange(userId, candidates), this::merchantIdentity);

        List<ImportError> errors = new ArrayList<>();
        validation.errors().forEach(error -> errors.add(new ImportError(jobId, error.rowNumber(), error.rawLine(),
                error.code(), error.message())));
        dedupe.duplicates().forEach(match -> errors.add(new ImportError(jobId, match.candidate().rowNumber(),
                match.candidate().rawLine(), "DUPLICATE",
                match.exact() ? "Same bank reference as an existing transaction"
                        : "Matches an existing transaction (same merchant and amount within one day)")));

        int preProcessed = validation.errors().size() + dedupe.duplicates().size();
        updateJob(jobId, job -> {
            job.setInvalidCount(validation.errors().size());
            job.setDuplicateCount(dedupe.duplicates().size());
            job.setTotalRowsProcessed(preProcessed);
        });

        Map<String, UserMerchantMapping> mappings = new HashMap<>();
        mappingRepository.findByUserId(userId)
                .forEach(m -> mappings.put(m.getRawMerchantName().toLowerCase(Locale.ROOT), m));
        Map<String, Merchant> merchantCache = new HashMap<>();

        List<Candidate> unique = dedupe.unique();
        int imported = 0;
        for (int start = 0; start < unique.size(); start += CHUNK_SIZE) {
            List<Candidate> chunk = unique.subList(start, Math.min(unique.size(), start + CHUNK_SIZE));
            Integer saved = transactionTemplate.execute(status -> {
                List<Transaction> batch = new ArrayList<>(chunk.size());
                for (Candidate candidate : chunk) {
                    batch.add(toTransaction(userId, accountId, currency, candidate, mappings, merchantCache));
                }
                transactionRepository.saveAll(batch);
                return batch.size();
            });
            imported += saved == null ? 0 : saved;
            int importedSoFar = imported;
            updateJob(jobId, job -> {
                job.setImportedCount(importedSoFar);
                job.setTotalRowsProcessed(preProcessed + importedSoFar);
            });
        }

        int importedTotal = imported;
        transactionTemplate.executeWithoutResult(status -> {
            errorRepository.saveAll(errors);
            ImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setImportStatus(ImportJob.COMPLETED);
            job.setImportedCount(importedTotal);
            job.setTotalRowsProcessed(parsed.rows().size());
            job.setCompletedAt(Times.nowUtc());
            job.setErrorSummary(summary(parsed, validation, errors));
            jobRepository.save(job);
        });
        if (importedTotal > 0) {
            events.publishEvent(new TransactionsChangedEvent(userId));
        }
        log.info("Import completed | jobId={} | imported={} | duplicates={} | invalid={}", jobId, importedTotal,
                dedupe.duplicates().size(), validation.errors().size());
    }

    private Transaction toTransaction(UUID userId, UUID accountId, String currency, Candidate candidate,
                                      Map<String, UserMerchantMapping> mappings, Map<String, Merchant> merchantCache) {
        MerchantNormalizer.Result normalized = merchantNormalizer.normalize(candidate.description());
        UserMerchantMapping mapping = mappings.get(candidate.description().toLowerCase(Locale.ROOT));
        if (mapping == null) {
            mapping = mappings.get(normalized.merchantName().toLowerCase(Locale.ROOT));
        }

        Merchant merchant;
        if (mapping != null) {
            UUID mappedId = mapping.getNormalizedMerchantId();
            merchant = merchantCache.computeIfAbsent("id:" + mappedId,
                    key -> merchantRepository.findById(mappedId).orElse(null));
        } else if (normalized.matched()) {
            merchant = normalized.merchant();
        } else {
            merchant = merchantCache.computeIfAbsent(normalized.merchantName().toLowerCase(Locale.ROOT),
                    key -> merchantService.findOrCreate(normalized.merchantName(), null));
        }

        Categorization categorization = categoryEngine.categorize(candidate.description(), merchant, mapping,
                candidate.type());

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setMerchantId(merchant == null ? null : merchant.getId());
        transaction.setCategoryId(categorization.categoryId());
        transaction.setSubcategoryId(categorization.subcategoryId());
        transaction.setCategorizationConfidence(categorization.confidence());
        transaction.setCategorizationSource(categorization.source());
        transaction.setAmount(candidate.amount());
        transaction.setCurrencyCode(currency);
        transaction.setTransactionType(candidate.type());
        transaction.setTransfer(Transaction.TRANSFER.equals(candidate.type()));
        transaction.setTransactionDate(candidate.date());
        transaction.setRawDescription(candidate.description());
        transaction.setDescription(merchant != null ? merchant.getMerchantName() : normalized.merchantName());
        transaction.setExternalReference(candidate.reference());
        transaction.setPaymentMethod(candidate.paymentMethod());
        return transaction;
    }

    /** Canonical merchant when a rule matches, otherwise the cleaned text. */
    private String merchantIdentity(String text) {
        MerchantNormalizer.Result result = merchantNormalizer.normalize(text);
        return result.matched() ? "m:" + result.merchant().getId() : "t:" + result.merchantName().toLowerCase(Locale.ROOT);
    }

    private List<Transaction> existingInRange(UUID userId, List<Candidate> candidates) {
        LocalDate min = candidates.stream().map(Candidate::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = candidates.stream().map(Candidate::date).max(LocalDate::compareTo).orElseThrow();
        return transactionTemplate.execute(status ->
                transactionRepository.findByUserIdAndTransactionDateBetween(userId, min.minusDays(1), max.plusDays(1)));
    }

    private String summary(ParsedFile parsed, StatementRowValidator.Result validation, List<ImportError> errors) {
        Map<String, Integer> byCode = new TreeMap<>();
        errors.forEach(error -> byCode.merge(error.getErrorCode(), 1, Integer::sum));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("errorsByCode", byCode);
        summary.put("delimiter", parsed.delimiter() == '\t' ? "tab" : String.valueOf(parsed.delimiter()));
        summary.put("hasHeader", parsed.hasHeader());
        summary.put("dateOrder", validation.dateOrder().name());
        summary.put("columns", parsed.mapping().describe());
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception exception) {
            return null;
        }
    }

    private void fail(UUID jobId, String message) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(jobId).ifPresent(job -> {
            job.setImportStatus(ImportJob.FAILED);
            job.setCompletedAt(Times.nowUtc());
            try {
                job.setErrorSummary(objectMapper.writeValueAsString(Map.of("error", message)));
            } catch (Exception ignored) {
                job.setErrorSummary(null);
            }
            jobRepository.save(job);
        }));
    }

    private void updateJob(UUID jobId, java.util.function.Consumer<ImportJob> change) {
        transactionTemplate.executeWithoutResult(status -> {
            ImportJob job = jobRepository.findById(jobId).orElseThrow();
            change.accept(job);
            jobRepository.save(job);
        });
    }
}
