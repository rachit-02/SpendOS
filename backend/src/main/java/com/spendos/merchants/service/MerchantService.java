package com.spendos.merchants.service;

import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.repository.MerchantRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantCreator creator;

    public MerchantService(MerchantRepository merchantRepository, MerchantCreator creator) {
        this.merchantRepository = merchantRepository;
        this.creator = creator;
    }

    /** Collapses whitespace and trims; the canonical display form of a merchant name. */
    public static String clean(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }

    /**
     * Returns the canonical merchant with this name (case-insensitive), creating it when new.
     * Concurrent creators of the same merchant race on the unique index; the loser re-reads.
     */
    @Transactional
    public Merchant findOrCreate(String name, UUID defaultCategoryId) {
        String cleaned = clean(name);
        String lower = cleaned.toLowerCase(Locale.ROOT);
        return merchantRepository.findByMerchantNameLower(lower).orElseGet(() -> {
            try {
                return creator.create(cleaned, defaultCategoryId);
            } catch (DataIntegrityViolationException raced) {
                return merchantRepository.findByMerchantNameLower(lower).orElseThrow(() -> raced);
            }
        });
    }

    /** Inserts in its own transaction so a unique-key race does not poison the caller's transaction. */
    @org.springframework.stereotype.Component
    public static class MerchantCreator {
        private final MerchantRepository merchantRepository;

        public MerchantCreator(MerchantRepository merchantRepository) {
            this.merchantRepository = merchantRepository;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Merchant create(String name, UUID categoryId) {
            return merchantRepository.saveAndFlush(new Merchant(name, categoryId));
        }
    }
}
