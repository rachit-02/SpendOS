package com.spendos.merchants.normalizer;

import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.domain.MerchantNormalizationRule;
import com.spendos.merchants.repository.MerchantNormalizationRuleRepository;
import com.spendos.merchants.repository.MerchantRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps raw statement text to a canonical merchant using the deterministic rules in
 * merchant_normalization_rules (lowest priority value first), falling back to text clean-up.
 * Rules are loaded once and cached; call {@link #reload()} after rules change.
 */
@Component
public class MerchantNormalizer {

    private static final Logger log = LoggerFactory.getLogger(MerchantNormalizer.class);
    public static final String SIMILAR = "similar";
    static final double FUZZY_THRESHOLD = 0.8;
    static final int MIN_FUZZY_LENGTH = 5;

    /** Result: the display name to use and, when a rule matched, the canonical merchant. */
    public record Result(String merchantName, Merchant merchant, String matchType, BigDecimal confidence) {
        public boolean matched() {
            return merchant != null;
        }
    }

    private record CompiledRule(MerchantNormalizationRule rule, Pattern regex) {
    }

    private final MerchantNormalizationRuleRepository ruleRepository;
    private final MerchantRepository merchantRepository;
    private volatile List<CompiledRule> rules;
    private volatile Map<UUID, Merchant> merchantsById;

    public MerchantNormalizer(MerchantNormalizationRuleRepository ruleRepository, MerchantRepository merchantRepository) {
        this.ruleRepository = ruleRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional(readOnly = true)
    public Result normalize(String rawText) {
        ensureLoaded();
        String cleaned = MerchantTextCleaner.clean(rawText);
        String haystack = ((rawText == null ? "" : rawText) + " " + cleaned).toLowerCase(Locale.ROOT);
        for (CompiledRule compiled : rules) {
            if (matches(compiled, haystack, cleaned.toLowerCase(Locale.ROOT))) {
                Merchant merchant = merchantsById.get(compiled.rule().getTargetMerchantId());
                if (merchant != null) {
                    BigDecimal confidence = MerchantNormalizationRule.EXACT.equals(compiled.rule().getPatternType())
                            ? new BigDecimal("0.99") : new BigDecimal("0.95");
                    return new Result(merchant.getMerchantName(), merchant, compiled.rule().getPatternType(), confidence);
                }
            }
        }
        Optional<Merchant> byName = Optional.ofNullable(merchantsByLowerName().get(cleaned.toLowerCase(Locale.ROOT)));
        return byName.map(m -> new Result(m.getMerchantName(), m, MerchantNormalizationRule.EXACT, new BigDecimal("0.90")))
                .or(() -> similar(cleaned))
                .orElseGet(() -> new Result(cleaned, null, "cleaned", new BigDecimal("0.50")));
    }

    /**
     * Last resort for typos and truncation ("SWIGY" → Swiggy): the closest known merchant by
     * Levenshtein ratio, only for names of at least five letters and a ratio of 0.8 or more.
     * Confidence scales with similarity and stays below rule matches.
     */
    private Optional<Result> similar(String cleaned) {
        if (cleaned.length() < MIN_FUZZY_LENGTH) {
            return Optional.empty();
        }
        Merchant best = null;
        double bestRatio = 0;
        for (Merchant merchant : merchantsById.values()) {
            double ratio = Similarity.ratio(cleaned, merchant.getMerchantName());
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = merchant;
            }
        }
        if (best == null || bestRatio < FUZZY_THRESHOLD) {
            return Optional.empty();
        }
        BigDecimal confidence = BigDecimal.valueOf(bestRatio * 0.9).setScale(2, java.math.RoundingMode.HALF_UP);
        return Optional.of(new Result(best.getMerchantName(), best, SIMILAR, confidence));
    }

    private static boolean matches(CompiledRule compiled, String haystack, String cleanedLower) {
        String pattern = compiled.rule().getPattern().toLowerCase(Locale.ROOT);
        return switch (compiled.rule().getPatternType()) {
            case MerchantNormalizationRule.EXACT -> cleanedLower.equals(pattern);
            case MerchantNormalizationRule.STARTS_WITH -> cleanedLower.startsWith(pattern);
            case MerchantNormalizationRule.CONTAINS -> haystack.contains(pattern);
            case MerchantNormalizationRule.REGEX -> compiled.regex() != null && compiled.regex().matcher(haystack).find();
            default -> false;
        };
    }

    private Map<String, Merchant> merchantsByLowerName() {
        return merchantsById.values().stream()
                .collect(Collectors.toMap(Merchant::getMerchantNameLower, Function.identity(), (a, b) -> a));
    }

    private void ensureLoaded() {
        if (rules == null) {
            synchronized (this) {
                if (rules == null) {
                    load();
                }
            }
        }
    }

    /** Re-reads rules and verified merchants; called after rules or merchant categories change. */
    public synchronized void reload() {
        load();
    }

    private void load() {
        List<CompiledRule> compiled = new ArrayList<>();
        for (MerchantNormalizationRule rule : ruleRepository.findByActiveTrueOrderByPriorityAsc()) {
            Pattern regex = null;
            if (MerchantNormalizationRule.REGEX.equals(rule.getPatternType())) {
                try {
                    regex = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException exception) {
                    log.warn("Skipping invalid merchant rule regex | ruleId={}", rule.getId());
                    continue;
                }
            }
            compiled.add(new CompiledRule(rule, regex));
        }
        List<UUID> targetIds = compiled.stream().map(c -> c.rule().getTargetMerchantId()).distinct().toList();
        Map<UUID, Merchant> byId = merchantRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
        merchantRepository.findAll().stream().filter(Merchant::isVerified).forEach(m -> byId.putIfAbsent(m.getId(), m));
        this.merchantsById = byId;
        this.rules = List.copyOf(compiled);
    }
}
