package com.spendos.recurring.service;

import com.spendos.analytics.service.PeriodResolver;
import com.spendos.analytics.service.UserDataCache;
import com.spendos.audit.service.AuditService;
import com.spendos.common.exception.ApiException;
import com.spendos.merchants.normalizer.MerchantTextCleaner;
import com.spendos.recurring.detector.RecurringDetector;
import com.spendos.recurring.detector.RecurringDetector.Detection;
import com.spendos.recurring.detector.RecurringDetector.Payment;
import com.spendos.recurring.domain.RecurringPayment;
import com.spendos.recurring.dto.RecurringDtos.DecisionResponse;
import com.spendos.recurring.dto.RecurringDtos.DetectionSummary;
import com.spendos.recurring.dto.RecurringDtos.RecurringResponse;
import com.spendos.recurring.repository.RecurringPaymentRepository;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.users.service.UserPreferencesService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the recurring detector over the user's last ~13 months of spending, stores the results and
 * flags the matching transactions. The user's decisions win: dismissed payments are never re-activated
 * and confirmed ones stay confirmed. Nothing is ever cancelled on the user's behalf.
 */
@Service
public class RecurringService {

    static final int LOOKBACK_DAYS = 400;

    private final RecurringPaymentRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final AuditService auditService;
    private final UserDataCache cache;

    public RecurringService(RecurringPaymentRepository recurringRepository, TransactionRepository transactionRepository,
                            UserPreferencesService preferencesService, PeriodResolver periods, AuditService auditService,
                            UserDataCache cache) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.auditService = auditService;
        this.cache = cache;
    }

    @Transactional
    public DetectionSummary detect(UUID userId) {
        LocalDate today = periods.today(userId);
        List<Transaction> debits = transactionRepository
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc(userId, today.minusDays(LOOKBACK_DAYS), today)
                .stream().filter(Transaction::isDebit).toList();
        Map<UUID, Transaction> byId = new HashMap<>();
        List<Payment> payments = debits.stream().map(t -> {
            byId.put(t.getId(), t);
            String name = t.getMerchant() != null ? t.getMerchant().getMerchantName()
                    : MerchantTextCleaner.clean(t.getRawDescription());
            String key = t.getMerchantId() != null ? "m:" + t.getMerchantId() : "t:" + name.toLowerCase(Locale.ROOT);
            return new Payment(t.getId(), key, t.getMerchantId(), name, t.getCategoryId(), t.getAmount(),
                    t.getTransactionDate());
        }).toList();

        List<Detection> detections = RecurringDetector.detect(payments, today);
        Map<String, RecurringPayment> existing = new HashMap<>();
        recurringRepository.findByUserId(userId).forEach(p -> existing.put(key(p.getMerchantId(), p.getMerchantName()), p));

        int created = 0;
        int updated = 0;
        String currency = preferencesService.currencyFor(userId);
        for (Detection detection : detections) {
            RecurringPayment payment = existing.get(key(detection.merchantId(), detection.merchantName()));
            boolean dismissed = payment != null && payment.isUserConfirmed() && !payment.isActive();
            if (payment == null) {
                payment = new RecurringPayment();
                payment.setUserId(userId);
                payment.setCurrencyCode(currency);
                created++;
            } else {
                updated++;
            }
            payment.setMerchantId(detection.merchantId());
            payment.setMerchantName(detection.merchantName());
            payment.setCategoryId(detection.categoryId());
            payment.setTypicalAmount(detection.typicalAmount());
            payment.setFrequency(detection.frequency().code);
            payment.setLastOccurrenceDate(detection.lastDate());
            payment.setNextExpectedDate(detection.nextExpectedDate());
            payment.setOccurrencesCount(detection.occurrences());
            payment.setConfidence(detection.confidence());
            if (!dismissed) {
                payment.setActive(detection.active());
            }
            recurringRepository.save(payment);
            boolean flag = !dismissed;
            for (UUID transactionId : detection.transactionIds()) {
                Transaction transaction = byId.get(transactionId);
                if (transaction != null && transaction.isRecurring() != flag) {
                    transaction.setRecurring(flag);
                }
            }
        }
        cache.evict(userId);
        int active = (int) recurringRepository.findByUserIdAndActiveTrue(userId).size();
        return new DetectionSummary(detections.size(), created, updated, active);
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId, String confirmed, String sortBy) {
        Comparator<RecurringResponse> order = switch (sortBy == null ? "nextDate" : sortBy) {
            case "amount" -> Comparator.comparing(RecurringResponse::monthlyCost,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "confidence" -> Comparator.comparing(RecurringResponse::confidence,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "nextDate" -> Comparator.comparing(RecurringResponse::nextExpectedDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> throw ApiException.badRequest("INVALID_REQUEST", "sortBy must be nextDate, amount or confidence");
        };
        return recurringRepository.findByUserId(userId).stream()
                .map(RecurringResponse::from)
                .filter(r -> switch (confirmed == null ? "all" : confirmed) {
                    case "true" -> r.isUserConfirmed() && r.isActive();
                    case "false" -> !r.isUserConfirmed();
                    case "all" -> !"dismissed".equals(r.status());
                    case "dismissed" -> "dismissed".equals(r.status());
                    default -> throw ApiException.badRequest("INVALID_REQUEST", "confirmed must be all, true, false or dismissed");
                })
                .sorted(order)
                .toList();
    }

    /** Active recurring payments expected within {@code days} days (used by affordability and the dashboard). */
    @Transactional(readOnly = true)
    public List<RecurringResponse> upcoming(UUID userId, int days) {
        LocalDate today = periods.today(userId);
        return list(userId, "all", "nextDate").stream()
                .filter(RecurringResponse::isActive)
                .filter(r -> r.nextExpectedDate() != null && !r.nextExpectedDate().isBefore(today)
                        && !r.nextExpectedDate().isAfter(today.plusDays(days)))
                .toList();
    }

    @Transactional
    public DecisionResponse confirm(UUID userId, UUID recurringId) {
        RecurringPayment payment = require(userId, recurringId);
        payment.setUserConfirmed(true);
        payment.setActive(true);
        setTransactionsRecurring(userId, payment, true);
        auditService.record(userId, "recurring_payment", recurringId, AuditService.UPDATE, null,
                Map.of("decision", "confirmed"));
        cache.evict(userId);
        return new DecisionResponse(payment.getId(), true, true, Instant.now());
    }

    /** Marks a detection as "not recurring"; it stays hidden in future detections. */
    @Transactional
    public DecisionResponse reject(UUID userId, UUID recurringId) {
        RecurringPayment payment = require(userId, recurringId);
        payment.setUserConfirmed(true);
        payment.setActive(false);
        setTransactionsRecurring(userId, payment, false);
        auditService.record(userId, "recurring_payment", recurringId, AuditService.UPDATE, null,
                Map.of("decision", "dismissed"));
        cache.evict(userId);
        return new DecisionResponse(payment.getId(), true, false, Instant.now());
    }

    private void setTransactionsRecurring(UUID userId, RecurringPayment payment, boolean recurring) {
        LocalDate today = periods.today(userId);
        transactionRepository.findByUserIdAndTransactionDateBetween(userId, today.minusDays(LOOKBACK_DAYS), today).stream()
                .filter(Transaction::isDebit)
                .filter(t -> payment.getMerchantId() != null ? payment.getMerchantId().equals(t.getMerchantId())
                        : payment.getMerchantName().equalsIgnoreCase(MerchantTextCleaner.clean(t.getRawDescription())))
                .forEach(t -> t.setRecurring(recurring));
    }

    private RecurringPayment require(UUID userId, UUID recurringId) {
        return recurringRepository.findByIdAndUserId(recurringId, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring payment not found"));
    }

    private static String key(UUID merchantId, String merchantName) {
        return merchantId != null ? "m:" + merchantId : "t:" + Objects.toString(merchantName, "").toLowerCase(Locale.ROOT);
    }
}
