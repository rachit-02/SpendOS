package com.spendos.merchants.service;

import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.domain.UserMerchantMapping;
import com.spendos.merchants.normalizer.MerchantNormalizer;
import com.spendos.merchants.repository.MerchantRepository;
import com.spendos.merchants.repository.UserMerchantMappingRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {

    /** A merchant for a user-typed name, plus the category the user mapped it to (if any). */
    public record Resolved(Merchant merchant, UUID mappedCategoryId) {
    }

    private final MerchantRepository merchantRepository;
    private final MerchantCreator creator;
    private final UserMerchantMappingRepository mappingRepository;
    private final MerchantNormalizer normalizer;

    public MerchantService(MerchantRepository merchantRepository, MerchantCreator creator,
                           UserMerchantMappingRepository mappingRepository, MerchantNormalizer normalizer) {
        this.merchantRepository = merchantRepository;
        this.creator = creator;
        this.mappingRepository = mappingRepository;
        this.normalizer = normalizer;
    }

    /**
     * Resolves a name the user typed the same way imports do: the user's own mapping first, then
     * the normalization rules (e.g. "UPI-NETFLIX" is Netflix), otherwise a merchant with that name.
     */
    @Transactional
    public Resolved resolve(UUID userId, String name) {
        String cleaned = clean(name);
        Optional<UserMerchantMapping> mapping = mappingRepository.findByUserIdAndRawMerchantNameIgnoreCase(userId, cleaned);
        Optional<Merchant> mapped = mapping.flatMap(m -> merchantRepository.findById(m.getNormalizedMerchantId()));
        if (mapped.isPresent()) {
            return new Resolved(mapped.get(), mapping.get().getCategoryId());
        }
        MerchantNormalizer.Result normalized = normalizer.normalize(cleaned);
        Merchant merchant = normalized.matched()
                ? merchantRepository.findById(normalized.merchant().getId()).orElseGet(() -> findOrCreate(cleaned, null))
                : findOrCreate(cleaned, null);
        UUID categoryId = merchant.getMerchantName().equalsIgnoreCase(cleaned) ? null
                : mappingRepository.findByUserIdAndRawMerchantNameIgnoreCase(userId, merchant.getMerchantName())
                        .map(UserMerchantMapping::getCategoryId).orElse(null);
        return new Resolved(merchant, categoryId);
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
