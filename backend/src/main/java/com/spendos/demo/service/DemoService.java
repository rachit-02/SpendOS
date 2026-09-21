package com.spendos.demo.service;

import com.spendos.auth.domain.User;
import com.spendos.auth.dto.AuthResponse;
import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.repository.UserRepository;
import com.spendos.budgets.dto.BudgetDtos.BudgetRequest;
import com.spendos.budgets.dto.BudgetDtos.CategoryAllocation;
import com.spendos.budgets.service.BudgetService;
import com.spendos.categories.domain.Category;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.security.JwtTokenProvider;
import com.spendos.demo.service.DemoDataGenerator.DemoTransaction;
import com.spendos.goals.dto.GoalDtos.GoalRequest;
import com.spendos.goals.service.GoalService;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.service.MerchantService;
import com.spendos.recurring.service.RecurringService;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.dto.AccountDtos.CreateAccountRequest;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.service.AccountService;
import com.spendos.transactions.service.TransactionsChangedEvent;
import com.spendos.users.domain.UserPreferences;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo accounts: a throwaway user filled with synthetic data, signed in immediately. The password
 * is random and never revealed, so a demo account is only reachable through the tokens issued here.
 * Demo accounts are removed after a day by {@link com.spendos.users.service.AccountPurgeService}.
 */
@Service
public class DemoService {

    public static final String DEMO_EMAIL_DOMAIN = "@demo.spendos.invalid";
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserPreferencesService preferencesService;
    private final AccountService accountService;
    private final MerchantService merchantService;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final RecurringService recurringService;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public DemoService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                       UserPreferencesService preferencesService, AccountService accountService,
                       MerchantService merchantService, CategoryService categoryService,
                       TransactionRepository transactionRepository, BudgetService budgetService, GoalService goalService,
                       RecurringService recurringService, JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.preferencesService = preferencesService;
        this.accountService = accountService;
        this.merchantService = merchantService;
        this.categoryService = categoryService;
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.recurringService = recurringService;
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public AuthResponse createDemoAccount() {
        String email = "demo-" + UUID.randomUUID().toString().substring(0, 12) + DEMO_EMAIL_DOMAIN;
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        User user = userRepository.save(new User(email, passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)),
                "Demo User"));
        UserPreferences preferences = preferencesService.createDefaults(user.getId());
        preferences.setDemoMode(true);
        seed(user.getId());
        log.info("Demo account created | userId={} | action=demo_create", user.getId());
        return new AuthResponse(tokenProvider.createAccessToken(user.getId(), user.getEmail()),
                tokenProvider.createRefreshToken(user.getId(), user.getEmail()),
                tokenProvider.getAccessTokenExpirationSeconds(), UserResponse.from(user));
    }

    /** Puts a demo account back to its initial state. Refused for real accounts. */
    @Transactional
    public void reset(UUID userId) {
        if (!preferencesService.getOrCreate(userId).isDemoMode()) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "NOT_A_DEMO_ACCOUNT",
                    "Only demo accounts can be reset");
        }
        for (String table : List.of("transactions", "budgets", "financial_goals", "recurring_payments", "insights",
                "monthly_reports", "simulations", "user_merchant_mappings", "financial_health_history",
                "financial_health_metrics", "import_jobs")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId); // fixed table names, never input
        }
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        seed(userId);
        log.info("Demo account reset | userId={} | action=demo_reset", userId);
    }

    private void seed(UUID userId) {
        LocalDate today = LocalDate.now(DEMO_ZONE);
        UUID accountId = accountService.create(userId, new CreateAccountRequest("Salary Account", "savings", "4821",
                "Demo Bank", true, "INR", new BigDecimal("150000"))).id();

        Map<String, UUID> categories = new HashMap<>();
        Map<String, Merchant> merchants = new HashMap<>();
        List<Transaction> batch = new ArrayList<>();
        for (DemoTransaction row : DemoDataGenerator.generate(today, userId.getMostSignificantBits())) {
            UUID categoryId = categories.computeIfAbsent(row.category(),
                    name -> categoryService.findByName(name).map(Category::getId).orElseThrow());
            Merchant merchant = merchants.computeIfAbsent(row.merchant().toLowerCase(Locale.ROOT),
                    key -> merchantService.findOrCreate(row.merchant(), null));
            Transaction transaction = new Transaction();
            transaction.setUserId(userId);
            transaction.setAccountId(accountId);
            transaction.setMerchantId(merchant.getId());
            transaction.setCategoryId(categoryId);
            transaction.setAmount(row.amount());
            transaction.setCurrencyCode("INR");
            transaction.setTransactionType(row.type());
            transaction.setTransactionDate(row.date());
            transaction.setRawDescription(row.merchant());
            transaction.setDescription(merchant.getMerchantName());
            transaction.setPaymentMethod(row.paymentMethod());
            transaction.setCategorizationSource(Transaction.SOURCE_RULE);
            transaction.setCategorizationConfidence(new BigDecimal("0.95"));
            batch.add(transaction);
        }
        transactionRepository.saveAll(batch);

        YearMonth month = YearMonth.from(today);
        budgetService.create(userId, new BudgetRequest("Monthly spending", "monthly", new BigDecimal("55000"),
                month.atDay(1), null, 90, true, List.of()));
        budgetService.create(userId, new BudgetRequest("Eating out", "monthly", new BigDecimal("9000"),
                month.atDay(1), null, 80, true, List.of(new CategoryAllocation(categories.get("Food"), new BigDecimal("9000")))));
        goalService.create(userId, new GoalRequest("Emergency fund", "Six months of expenses", "savings",
                new BigDecimal("300000"), new BigDecimal("90000"), today.plusMonths(12), true));
        goalService.create(userId, new GoalRequest("Goa trip", null, "savings", new BigDecimal("60000"),
                new BigDecimal("12000"), today.plusMonths(5), true));
        transactionRepository.flush();
        recurringService.detect(userId);
        events.publishEvent(new TransactionsChangedEvent(userId));
    }
}
