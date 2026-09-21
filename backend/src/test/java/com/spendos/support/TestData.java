package com.spendos.support;

import com.spendos.categories.domain.Category;
import com.spendos.categories.repository.CategoryRepository;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.service.MerchantService;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.AccountRepository;
import com.spendos.transactions.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Inserts fixtures directly through repositories (faster than going through the API). */
@Component
public class TestData {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantService merchantService;

    public TestData(AccountRepository accountRepository, TransactionRepository transactionRepository,
                    CategoryRepository categoryRepository, MerchantService merchantService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.merchantService = merchantService;
    }

    @Transactional
    public Account account(UUID userId) {
        Account account = new Account();
        account.setUserId(userId);
        account.setAccountName("Test account");
        account.setAccountType("savings");
        account.setPrimary(accountRepository.countByUserId(userId) == 0);
        return accountRepository.save(account);
    }

    public UUID categoryId(String name) {
        return categoryRepository.findByCategoryNameIgnoreCase(name).map(Category::getId).orElseThrow();
    }

    @Transactional
    public Transaction debit(UUID userId, UUID accountId, String merchant, String category, String amount, LocalDate date) {
        return save(userId, accountId, merchant, category, amount, date, Transaction.DEBIT);
    }

    @Transactional
    public Transaction credit(UUID userId, UUID accountId, String merchant, String category, String amount, LocalDate date) {
        return save(userId, accountId, merchant, category, amount, date, Transaction.CREDIT);
    }

    @Transactional
    public Transaction save(UUID userId, UUID accountId, String merchantName, String category, String amount,
                            LocalDate date, String type) {
        Merchant merchant = merchantService.findOrCreate(merchantName, null);
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setMerchantId(merchant.getId());
        transaction.setCategoryId(categoryId(category));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionType(type);
        transaction.setTransactionDate(date);
        transaction.setRawDescription(merchantName);
        transaction.setPaymentMethod("upi");
        transaction.setCategorizationSource(Transaction.SOURCE_RULE);
        return transactionRepository.save(transaction);
    }
}
