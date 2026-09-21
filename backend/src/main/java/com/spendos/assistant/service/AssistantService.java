package com.spendos.assistant.service;

import static com.spendos.common.util.MoneyText.money;
import static com.spendos.common.util.MoneyText.signedMoney;
import static com.spendos.common.util.MoneyText.signedPercent;

import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.MerchantAmount;
import com.spendos.analytics.repository.AggregateQueries.SubcategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.Totals;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.assistant.dto.AssistantDtos.AssistantAnswer;
import com.spendos.assistant.dto.AssistantDtos.AssistantContext;
import com.spendos.assistant.dto.AssistantDtos.Finding;
import com.spendos.assistant.dto.AssistantDtos.QueryRequest;
import com.spendos.assistant.dto.AssistantDtos.RelatedTransaction;
import com.spendos.assistant.dto.AssistantDtos.Suggestion;
import com.spendos.assistant.dto.AssistantDtos.SupportingData;
import com.spendos.assistant.engine.Intent;
import com.spendos.assistant.engine.QuestionParser;
import com.spendos.assistant.engine.QuestionParser.CategoryRef;
import com.spendos.assistant.engine.QuestionParser.MerchantRef;
import com.spendos.assistant.engine.QuestionParser.ParsedQuestion;
import com.spendos.assistant.engine.QuestionParser.Vocabulary;
import com.spendos.assistant.llm.ExplanationService;
import com.spendos.assistant.llm.ExplanationService.Explained;
import com.spendos.assistant.repository.AssistantQueries;
import com.spendos.assistant.repository.AssistantQueries.Filter;
import com.spendos.assistant.repository.AssistantQueries.MerchantTotal;
import com.spendos.categories.domain.Category;
import com.spendos.categories.domain.Subcategory;
import com.spendos.categories.repository.CategoryRepository;
import com.spendos.categories.repository.SubcategoryRepository;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Money;
import com.spendos.common.util.MoneyText;
import com.spendos.common.util.Percentages;
import com.spendos.recurring.dto.RecurringDtos.RecurringResponse;
import com.spendos.recurring.service.RecurringService;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Financial assistant: question → intent and entities → user-scoped queries → deterministic
 * calculations → a template answer with its supporting data. A language model may reword the
 * answer afterwards (see {@link ExplanationService}) but never supplies a figure.
 */
@Service
public class AssistantService {

    private static final int DEFAULT_LIMIT = 5;
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of("income", "transfers", "other");
    private static final Set<String> AMBIGUOUS_ALIASES = Set.of("general", "other", "interest", "refunds");
    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "food", List.of("eating out", "dining", "dining out", "restaurant", "takeout", "take-out", "meals", "lunch", "dinner"),
            "transport", List.of("commute", "commuting", "cabs", "taxi", "taxis", "petrol"),
            "shopping", List.of("clothes", "shoes"),
            "bills", List.of("utilities", "utility"),
            "healthcare", List.of("health", "medical", "medicine", "medicines", "doctor", "pharmacy", "hospital"),
            "entertainment", List.of("movies", "movie", "games", "gaming"),
            "education", List.of("courses", "course", "tuition", "school"),
            "travel", List.of("trips", "trip", "flights", "flight", "hotels", "hotel", "vacation", "holiday"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private record Window(YearMonth month, LocalDate start, LocalDate end, boolean partial) {
    }

    private record Named(UUID id, String name, BigDecimal amount) {
    }

    private record Draft(String period, String answer, List<Finding> findings, List<RelatedTransaction> transactions,
                         List<String> followUps) {
    }

    /** Per-request state shared by the handlers. */
    private record Ask(UUID userId, ParsedQuestion parsed, YearMonth reference, YearMonth latest, String currency) {
    }

    private final AggregateQueries aggregates;
    private final AssistantQueries queries;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final RecurringService recurringService;
    private final PeriodResolver periods;
    private final UserPreferencesService preferences;
    private final ExplanationService explanations;

    public AssistantService(AggregateQueries aggregates, AssistantQueries queries, CategoryRepository categoryRepository,
                            SubcategoryRepository subcategoryRepository, RecurringService recurringService,
                            PeriodResolver periods, UserPreferencesService preferences, ExplanationService explanations) {
        this.aggregates = aggregates;
        this.queries = queries;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.recurringService = recurringService;
        this.periods = periods;
        this.preferences = preferences;
        this.explanations = explanations;
    }

    public AssistantAnswer answer(UUID userId, QueryRequest request) {
        String question = request.question().trim();
        YearMonth latest = YearMonth.from(periods.today(userId));
        YearMonth reference = contextMonth(request.context(), latest);
        ParsedQuestion parsed = QuestionParser.parse(question, vocabulary(userId), reference, latest);
        Ask ask = new Ask(userId, parsed, reference, latest, preferences.currencyFor(userId));

        YearMonth future = parsed.months().stream().filter(m -> m.isAfter(latest)).findFirst().orElse(null);
        Draft draft = future != null ? futureMonth(future) : switch (parsed.intent()) {
            case SPENDING_CHANGE -> spendingChange(ask);
            case TOP_CATEGORIES -> topCategories(ask);
            case CATEGORY_SPEND -> categorySpend(ask);
            case MERCHANT_SPEND -> merchantSpend(ask);
            case LARGEST_PURCHASES -> largestPurchases(ask);
            case SUBSCRIPTIONS -> subscriptions(ask);
            case COMPARE_MONTHS -> compareMonths(ask);
            case MONTH_SUMMARY -> monthSummary(ask);
            case UNKNOWN -> unknown(userId, latest);
        };
        Explained explained = parsed.intent() == Intent.UNKNOWN || future != null
                ? new Explained(draft.answer(), ExplanationService.RULES)
                : explanations.explain(question, draft.answer(), draft.findings());
        return new AssistantAnswer(question, parsed.intent().code(), draft.period(), explained.text(),
                new SupportingData(draft.findings(), draft.transactions()), draft.followUps(), explained.source(),
                ask.currency());
    }

    /** Suggested questions, personalised with the user's own months, top category and subscriptions. */
    public List<Suggestion> suggestions(UUID userId) {
        YearMonth latest = YearMonth.from(periods.today(userId));
        Window now = window(userId, latest);
        List<CategoryAmount> categories = aggregates.spendingByCategory(userId, now.start(), now.end());
        if (categories.isEmpty()) {
            Window last = window(userId, latest.minusMonths(1));
            categories = aggregates.spendingByCategory(userId, last.start(), last.end());
        }
        String topCategory = categories.stream().map(CategoryAmount::categoryName)
                .filter(name -> !"Uncategorized".equals(name)).findFirst().orElse("food");
        List<Suggestion> suggestions = new ArrayList<>();
        suggestions.add(new Suggestion("Why did I spend more this month?", Intent.SPENDING_CHANGE.code()));
        suggestions.add(new Suggestion("Where did most of my money go?", Intent.TOP_CATEGORIES.code()));
        suggestions.add(new Suggestion("How much did I spend on " + topCategory + "?", Intent.CATEGORY_SPEND.code()));
        suggestions.add(new Suggestion("What were my largest purchases?", Intent.LARGEST_PURCHASES.code()));
        suggestions.add(new Suggestion("Which subscriptions do I have?", Intent.SUBSCRIPTIONS.code()));
        suggestions.add(new Suggestion("Compare " + monthName(latest.minusMonths(1)) + " and " + monthName(latest),
                Intent.COMPARE_MONTHS.code()));
        suggestions.add(new Suggestion("How much did I save this month?", Intent.MONTH_SUMMARY.code()));
        return suggestions;
    }

    // ----- handlers ---------------------------------------------------------------------------

    private Draft spendingChange(Ask ask) {
        List<YearMonth> months = ask.parsed().months().stream().sorted().toList();
        YearMonth current = months.isEmpty() ? ask.reference() : months.get(months.size() - 1);
        YearMonth baseline = months.size() >= 2 ? months.get(0) : current.minusMonths(1);
        Window now = window(ask.userId(), current);
        LocalDate baseEnd = baseline.atEndOfMonth();
        String baseLabel = label(baseline, ask);
        if (now.partial() && months.size() < 2) {
            // Like for like: the same number of days of the previous month.
            int days = now.end().getDayOfMonth();
            baseEnd = baseline.atDay(Math.min(days, baseline.lengthOfMonth()));
            baseLabel = "the first " + days + " days of " + label(baseline, ask);
        }
        String nowLabel = label(current, ask) + (now.partial() ? " so far" : "");
        CategoryRef category = ask.parsed().category();
        String currency = ask.currency();

        List<Named> nowParts;
        List<Named> baseParts;
        if (category != null) {
            nowParts = merchantParts(ask.userId(), now.start(), now.end(), category.id());
            baseParts = merchantParts(ask.userId(), baseline.atDay(1), baseEnd, category.id());
        } else {
            nowParts = categoryParts(ask.userId(), now.start(), now.end());
            baseParts = categoryParts(ask.userId(), baseline.atDay(1), baseEnd);
        }
        BigDecimal nowTotal = sum(nowParts);
        BigDecimal baseTotal = sum(baseParts);
        String subject = category == null ? "" : " on " + category.name();
        String period = periodLabel(current);
        List<Finding> deltas = deltas(nowParts, baseParts, category != null);

        if (nowTotal.signum() == 0 && baseTotal.signum() == 0) {
            return new Draft(period, "I couldn't find any spending" + subject + " in " + nowLabel + " or " + baseLabel + ".",
                    List.of(), List.of(), List.of("Where did most of my money go?"));
        }
        if (baseTotal.signum() == 0) {
            return new Draft(period, "You spent " + money(nowTotal, currency) + subject + " in " + nowLabel
                    + ", and nothing in " + baseLabel + ", so there is nothing to compare against.",
                    deltas, queries.largestDebits(ask.userId(), now.start(), now.end(), categoryFilter(category), DEFAULT_LIMIT),
                    List.of("Where did most of my money go?"));
        }
        BigDecimal difference = nowTotal.subtract(baseTotal);
        int direction = difference.signum();
        StringBuilder text = new StringBuilder();
        if (direction == 0) {
            text.append("You spent the same").append(subject).append(" in ").append(nowLabel).append(" as in ")
                    .append(baseLabel).append(": ").append(money(nowTotal, currency)).append('.');
        } else {
            boolean mismatch = (direction > 0) == ask.parsed().asksAboutLess();
            if (mismatch) {
                text.append("Actually, you spent ").append(direction > 0 ? "more" : "less").append(". ");
            }
            text.append("You spent ").append(money(nowTotal, currency)).append(subject).append(" in ").append(nowLabel)
                    .append(", ").append(money(difference.abs(), currency)).append(" (")
                    .append(signedPercent(Money.percentChange(nowTotal, baseTotal))).append(") ")
                    .append(direction > 0 ? "more" : "less").append(" than ").append(money(baseTotal, currency))
                    .append(" in ").append(baseLabel).append('.');
        }
        List<Finding> drivers = deltas.stream().filter(d -> d.change().signum() == direction && direction != 0)
                .limit(3).toList();
        if (!drivers.isEmpty()) {
            text.append(" The biggest ").append(direction > 0 ? "increase" : "decrease")
                    .append(drivers.size() == 1 ? " was " : "s were ")
                    .append(joinWithAnd(drivers.stream().map(d -> name(d) + " (" + money(d.thisMonth(), currency) + " vs "
                            + money(d.previousMonth(), currency) + ", " + changeText(d) + ")").toList()))
                    .append('.');
        }
        deltas.stream().filter(d -> direction != 0 && d.change().signum() == -direction).findFirst().ifPresent(offset ->
                text.append(" This was partly offset by ").append(direction > 0 ? "lower " : "higher ").append(name(offset))
                        .append(" spending (").append(signedMoney(offset.change(), currency)).append(")."));

        List<RelatedTransaction> related;
        if (drivers.isEmpty()) {
            related = queries.largestDebits(ask.userId(), now.start(), now.end(), categoryFilter(category), DEFAULT_LIMIT);
        } else if (category != null) {
            UUID merchantId = idOf(nowParts, name(drivers.get(0)));
            related = queries.largestDebits(ask.userId(), now.start(), now.end(),
                    new Filter(category.id(), null, merchantId), DEFAULT_LIMIT);
        } else {
            UUID categoryId = idOf(nowParts, name(drivers.get(0)));
            related = queries.largestDebits(ask.userId(), now.start(), now.end(),
                    new Filter(categoryId, null, null), DEFAULT_LIMIT);
        }
        List<String> followUps = new ArrayList<>();
        if (!drivers.isEmpty() && category == null) {
            followUps.add("How much did I spend on " + name(drivers.get(0)) + "?");
        }
        followUps.add("What were my largest purchases in " + monthName(current) + "?");
        followUps.add("Compare " + monthName(baseline) + " and " + monthName(current));
        return new Draft(period, text.toString(), deltas.stream().limit(5).toList(), related, followUps);
    }

    private Draft topCategories(Ask ask) {
        Window w = window(ask.userId(), singleMonth(ask));
        String label = label(w.month(), ask) + (w.partial() ? " so far" : "");
        List<CategoryAmount> categories = aggregates.spendingByCategory(ask.userId(), w.start(), w.end());
        if (categories.isEmpty()) {
            return noSpending(w, label);
        }
        List<BigDecimal> shares = Percentages.ofTotal(categories.stream().map(CategoryAmount::amount).toList());
        BigDecimal total = categories.stream().map(CategoryAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int limit = ask.parsed().limit() == null ? DEFAULT_LIMIT : ask.parsed().limit();
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, categories.size()); i++) {
            CategoryAmount c = categories.get(i);
            findings.add(Finding.amount(c.categoryName(), null, Money.scale(c.amount()), shares.get(i), c.count()));
        }
        List<Finding> leaders = findings.stream().limit(3).toList();
        String currency = ask.currency();
        StringBuilder text = new StringBuilder("In ").append(label).append(" you spent ").append(money(total, currency))
                .append(". Most of it went to ")
                .append(joinWithAnd(leaders.stream().map(f -> f.category() + " (" + money(f.amount(), currency) + ", "
                        + MoneyText.percent(f.percentage()) + ")").toList()))
                .append('.');
        if (leaders.size() > 1 && leaders.size() < categories.size()) {
            BigDecimal together = leaders.stream().map(Finding::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
            text.append(" Together that is ").append(MoneyText.percent(together)).append(" of your spending.");
        }
        return new Draft(periodLabel(w.month()), text.toString(), findings,
                queries.largestDebits(ask.userId(), w.start(), w.end(), Filter.NONE, DEFAULT_LIMIT),
                List.of("How much did I spend on " + leaders.get(0).category() + "?",
                        "Why did I spend more in " + monthName(w.month()) + "?",
                        "What were my largest purchases in " + monthName(w.month()) + "?"));
    }

    private Draft categorySpend(Ask ask) {
        CategoryRef category = ask.parsed().category();
        Window w = window(ask.userId(), singleMonth(ask));
        YearMonth previous = w.month().minusMonths(1);
        String label = label(w.month(), ask) + (w.partial() ? " so far" : "");
        String name = category.subcategoryName() != null ? category.subcategoryName() : category.name();
        String currency = ask.currency();

        Named now = categoryAmount(ask.userId(), category, w.start(), w.end());
        Named before = categoryAmount(ask.userId(), category, previous.atDay(1), previous.atEndOfMonth());
        long count = now.id() == null ? 0 : countFor(ask.userId(), category, w);
        StringBuilder text = new StringBuilder();
        if (now.amount().signum() == 0) {
            text.append("You haven't spent anything on ").append(name).append(" in ").append(label).append('.');
            if (before.amount().signum() > 0) {
                text.append(" In ").append(label(previous, ask)).append(" you spent ").append(money(before.amount(), currency))
                        .append('.');
            }
        } else {
            text.append("You spent ").append(money(now.amount(), currency)).append(" on ").append(name).append(" in ")
                    .append(label).append(" across ").append(count).append(count == 1 ? " transaction." : " transactions.");
            if (before.amount().signum() > 0) {
                int direction = now.amount().compareTo(before.amount());
                if (direction == 0) {
                    text.append(" That is the same as in ").append(label(previous, ask)).append('.');
                } else {
                    text.append(" That is ").append(MoneyText.percent(Money.percentChange(now.amount(), before.amount()).abs()))
                            .append(direction > 0 ? " more" : " less").append(" than ")
                            .append(money(before.amount(), currency)).append(" in ").append(label(previous, ask));
                    if (w.partial()) {
                        text.append(" (a full month)");
                    }
                    text.append('.');
                }
            }
            if (category.subcategoryId() == null) {
                List<MerchantAmount> merchants = aggregates.topMerchants(ask.userId(), w.start(), w.end(), category.id(), 3);
                if (!merchants.isEmpty()) {
                    text.append(" Most of it went to ").append(joinWithAnd(merchants.stream()
                            .map(m -> m.merchantName() + " (" + money(m.amount(), currency) + ")").toList())).append('.');
                }
            }
        }
        Finding finding = new Finding(name, null, Money.scale(now.amount()), Money.scale(before.amount()),
                Money.scale(now.amount().subtract(before.amount())), Money.percentChange(now.amount(), before.amount()),
                null, null, count, null);
        return new Draft(periodLabel(w.month()), text.toString(), List.of(finding),
                queries.largestDebits(ask.userId(), w.start(), w.end(), categoryFilter(category), DEFAULT_LIMIT),
                List.of("Why did I spend more on " + name + " in " + monthName(w.month()) + "?",
                        "Where did most of my money go in " + monthName(w.month()) + "?"));
    }

    private Draft merchantSpend(Ask ask) {
        MerchantRef merchant = ask.parsed().merchant();
        Window w = window(ask.userId(), singleMonth(ask));
        YearMonth previous = w.month().minusMonths(1);
        String label = label(w.month(), ask) + (w.partial() ? " so far" : "");
        String currency = ask.currency();
        MerchantTotal now = queries.merchantTotal(ask.userId(), merchant.id(), w.start(), w.end());
        MerchantTotal before = queries.merchantTotal(ask.userId(), merchant.id(), previous.atDay(1), previous.atEndOfMonth());

        StringBuilder text = new StringBuilder();
        if (now.count() == 0) {
            text.append("You haven't spent anything at ").append(merchant.name()).append(" in ").append(label).append('.');
            if (before.count() > 0) {
                text.append(" In ").append(label(previous, ask)).append(" you spent ").append(money(before.amount(), currency))
                        .append(" there.");
            }
        } else {
            BigDecimal average = now.amount().divide(BigDecimal.valueOf(now.count()), 2, RoundingMode.HALF_UP);
            text.append("You spent ").append(money(now.amount(), currency)).append(" at ").append(merchant.name())
                    .append(" in ").append(label).append(" across ").append(now.count())
                    .append(now.count() == 1 ? " transaction." : " transactions (" + money(average, currency) + " on average).");
            if (before.count() > 0) {
                text.append(" In ").append(label(previous, ask)).append(" you spent ").append(money(before.amount(), currency))
                        .append(" there.");
            }
        }
        Finding finding = new Finding(null, merchant.name(), Money.scale(now.amount()), Money.scale(before.amount()),
                Money.scale(now.amount().subtract(before.amount())), Money.percentChange(now.amount(), before.amount()),
                null, null, now.count(), null);
        return new Draft(periodLabel(w.month()), text.toString(), List.of(finding),
                queries.largestDebits(ask.userId(), w.start(), w.end(), new Filter(null, null, merchant.id()), DEFAULT_LIMIT),
                List.of("Where did most of my money go in " + monthName(w.month()) + "?",
                        "Which subscriptions do I have?"));
    }

    private Draft largestPurchases(Ask ask) {
        Window w = window(ask.userId(), singleMonth(ask));
        String label = label(w.month(), ask) + (w.partial() ? " so far" : "");
        int limit = ask.parsed().limit() == null ? DEFAULT_LIMIT : ask.parsed().limit();
        List<RelatedTransaction> largest = queries.largestDebits(ask.userId(), w.start(), w.end(), Filter.NONE, limit);
        if (largest.isEmpty()) {
            return noSpending(w, label);
        }
        String currency = ask.currency();
        BigDecimal total = aggregates.totals(ask.userId(), w.start(), w.end()).expense();
        BigDecimal sum = largest.stream().map(RelatedTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String items = joinWithAnd(largest.stream()
                .map(t -> money(t.amount(), currency) + " at " + displayName(t) + " on " + DAY.format(t.date())).toList());
        String text = (largest.size() == 1 ? "Your largest purchase in " + label + " was " : "Your " + largest.size()
                + " largest purchases in " + label + " were ") + items + ".";
        if (largest.size() > 1) {
            text += " Together they make up " + MoneyText.percent(Money.percent(sum, total)) + " of your "
                    + money(total, currency) + " spending.";
        }
        List<Finding> findings = largest.stream()
                .map(t -> Finding.amount(t.category(), displayName(t), Money.scale(t.amount()), Money.percent(t.amount(), total), null))
                .toList();
        return new Draft(periodLabel(w.month()), text, findings, largest,
                List.of("Where did most of my money go in " + monthName(w.month()) + "?",
                        "Why did I spend more in " + monthName(w.month()) + "?"));
    }

    private Draft subscriptions(Ask ask) {
        String currency = ask.currency();
        List<RecurringResponse> active = recurringService.list(ask.userId(), "all", "amount").stream()
                .filter(r -> "confirmed".equals(r.status()) || "pending".equals(r.status()))
                .toList();
        if (active.isEmpty()) {
            return new Draft("current", "I haven't found any recurring payments yet. They are detected automatically once"
                    + " the same payment repeats a few times.", List.of(), List.of(),
                    List.of("What were my largest purchases?"));
        }
        BigDecimal monthly = active.stream().map(r -> Money.nz(r.monthlyCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        int shown = Math.min(6, active.size());
        List<String> items = new ArrayList<>(active.subList(0, shown).stream()
                .map(r -> r.merchantName() + " " + money(r.typicalAmount(), currency) + " " + r.frequency()).toList());
        if (active.size() > shown) {
            items.add((active.size() - shown) + " more");
        }
        StringBuilder text = new StringBuilder("You have ").append(active.size()).append(" active recurring payment")
                .append(active.size() == 1 ? "" : "s").append(" costing about ").append(money(monthly, currency))
                .append(" a month (").append(money(monthly.multiply(BigDecimal.valueOf(12)), currency)).append(" a year): ")
                .append(joinWithAnd(items)).append('.');
        long pending = active.stream().filter(r -> "pending".equals(r.status())).count();
        if (pending > 0) {
            text.append(' ').append(pending).append(pending == 1 ? " of them was" : " of them were")
                    .append(" detected automatically and is waiting for you to confirm it on the Recurring page.");
        }
        List<Finding> findings = active.stream()
                .map(r -> Finding.recurring(r.merchantName(), Money.scale(r.typicalAmount()), r.frequency())).toList();
        return new Draft("current", text.toString(), findings, List.of(),
                List.of("How much did I spend on subscriptions?", "Where did most of my money go?"));
    }

    private Draft compareMonths(Ask ask) {
        List<YearMonth> months = ask.parsed().months().stream().sorted().toList();
        YearMonth later = months.isEmpty() ? ask.reference() : months.get(months.size() - 1);
        YearMonth earlier = months.size() >= 2 ? months.get(0) : later.minusMonths(1);
        if (!earlier.isBefore(later)) {
            earlier = later.minusMonths(1);
        }
        Window a = window(ask.userId(), earlier);
        Window b = window(ask.userId(), later);
        Totals ta = aggregates.totals(ask.userId(), a.start(), a.end());
        Totals tb = aggregates.totals(ask.userId(), b.start(), b.end());
        String la = label(earlier, ask);
        String lb = label(later, ask);
        String currency = ask.currency();
        List<Finding> deltas = deltas(categoryParts(ask.userId(), b.start(), b.end()),
                categoryParts(ask.userId(), a.start(), a.end()), false);

        StringBuilder text = new StringBuilder();
        if (ta.expense().signum() == 0) {
            text.append("There is no spending recorded in ").append(la).append("; in ").append(lb)
                    .append(b.partial() ? " so far" : "").append(" you spent ").append(money(tb.expense(), currency)).append('.');
        } else {
            BigDecimal difference = tb.expense().subtract(ta.expense());
            text.append("In ").append(lb).append(b.partial() ? " so far" : "").append(" you spent ")
                    .append(money(tb.expense(), currency));
            if (difference.signum() == 0) {
                text.append(", the same as in ").append(la).append('.');
            } else {
                text.append(", ").append(money(difference.abs(), currency)).append(" (")
                        .append(signedPercent(Money.percentChange(tb.expense(), ta.expense()))).append(") ")
                        .append(difference.signum() > 0 ? "more" : "less").append(" than in ").append(la).append(" (")
                        .append(money(ta.expense(), currency)).append(").");
            }
        }
        text.append(" Income was ").append(money(tb.income(), currency)).append(" vs ").append(money(ta.income(), currency))
                .append(", leaving net savings of ").append(signedMoney(tb.net(), currency)).append(" vs ")
                .append(signedMoney(ta.net(), currency)).append('.');
        List<Finding> changes = deltas.stream().filter(d -> d.change().signum() != 0).limit(3).toList();
        if (!changes.isEmpty()) {
            text.append(" The biggest change").append(changes.size() == 1 ? " was " : "s were ")
                    .append(joinWithAnd(changes.stream().map(d -> d.category() + " (" + signedMoney(d.change(), currency) + ")").toList()))
                    .append('.');
        }
        if (b.partial()) {
            text.append(' ').append(lb).append(" is still in progress (").append(b.end().getDayOfMonth()).append(" of ")
                    .append(later.lengthOfMonth()).append(" days).");
        }
        List<Finding> findings = new ArrayList<>();
        findings.add(Finding.comparison("Total spending", null, Money.scale(tb.expense()), Money.scale(ta.expense()),
                Money.scale(tb.expense().subtract(ta.expense())), Money.percentChange(tb.expense(), ta.expense())));
        findings.addAll(deltas.stream().limit(5).toList());
        return new Draft(periodLabel(earlier) + " vs " + periodLabel(later), text.toString(), findings,
                queries.largestDebits(ask.userId(), b.start(), b.end(), Filter.NONE, DEFAULT_LIMIT),
                List.of("Why did I spend more in " + monthName(later) + "?",
                        "Where did most of my money go in " + monthName(later) + "?"));
    }

    private Draft monthSummary(Ask ask) {
        Window w = window(ask.userId(), singleMonth(ask));
        String label = label(w.month(), ask) + (w.partial() ? " so far" : "");
        Totals totals = aggregates.totals(ask.userId(), w.start(), w.end());
        if (totals.incomeCount() + totals.expenseCount() == 0) {
            return new Draft(periodLabel(w.month()), "I couldn't find any transactions in " + label
                    + ". Import a statement for that month to get started.", List.of(), List.of(),
                    List.of("Where did most of my money go?"));
        }
        String currency = ask.currency();
        BigDecimal net = totals.net();
        StringBuilder text = new StringBuilder("In ").append(label).append(" you earned ").append(money(totals.income(), currency))
                .append(" and spent ").append(money(totals.expense(), currency));
        BigDecimal rate = null;
        if (net.signum() >= 0) {
            text.append(", saving ").append(money(net, currency));
            if (totals.income().signum() > 0) {
                rate = Money.percent(net, totals.income());
                text.append(" (").append(MoneyText.percent(rate)).append(" of your income)");
            }
            text.append('.');
        } else {
            text.append(", which is ").append(money(net.abs(), currency)).append(" more than you earned.");
        }
        List<CategoryAmount> categories = aggregates.spendingByCategory(ask.userId(), w.start(), w.end());
        if (!categories.isEmpty()) {
            text.append(" Your biggest spending category was ").append(categories.get(0).categoryName()).append(" (")
                    .append(money(categories.get(0).amount(), currency)).append(").");
        }
        List<Finding> findings = List.of(
                Finding.amount("Income", null, Money.scale(totals.income()), null, totals.incomeCount()),
                Finding.amount("Spending", null, Money.scale(totals.expense()), null, totals.expenseCount()),
                Finding.amount("Savings", null, Money.scale(net), rate, null));
        return new Draft(periodLabel(w.month()), text.toString(), findings,
                queries.largestDebits(ask.userId(), w.start(), w.end(), Filter.NONE, 3),
                List.of("Where did most of my money go in " + monthName(w.month()) + "?",
                        "Compare " + monthName(w.month().minusMonths(1)) + " and " + monthName(w.month())));
    }

    private Draft unknown(UUID userId, YearMonth latest) {
        List<String> examples = suggestions(userId).stream().map(Suggestion::question).limit(4).toList();
        return new Draft(periodLabel(latest), "I can answer questions about your own spending: where your money went, how much"
                + " you spent on a category or at a merchant, your largest purchases, your subscriptions, how two months"
                + " compare, or why your spending changed. Try one of the suggestions below.",
                List.of(), List.of(), examples);
    }

    private Draft futureMonth(YearMonth month) {
        return new Draft(periodLabel(month), periodLabel(month) + " hasn't happened yet, so there is no spending to report."
                + " The Planning page has a forecast for this month.", List.of(), List.of(),
                List.of("How much did I save this month?"));
    }

    private Draft noSpending(Window w, String label) {
        return new Draft(periodLabel(w.month()), "I couldn't find any spending in " + label + ".", List.of(), List.of(),
                List.of("Compare " + monthName(w.month().minusMonths(1)) + " and " + monthName(w.month())));
    }

    // ----- helpers ----------------------------------------------------------------------------

    private Vocabulary vocabulary(UUID userId) {
        Map<String, CategoryRef> aliases = new HashMap<>();
        Map<UUID, Category> categories = new LinkedHashMap<>();
        for (Category category : categoryRepository.findAllByOrderByDisplayOrderAscCategoryNameAsc()) {
            String name = category.getCategoryName().toLowerCase(Locale.ROOT);
            if (EXCLUDED_CATEGORIES.contains(name)) {
                continue;
            }
            categories.put(category.getId(), category);
            CategoryRef ref = new CategoryRef(category.getId(), category.getCategoryName(), null, null);
            addAlias(aliases, name, ref);
            SYNONYMS.getOrDefault(name, List.of()).forEach(alias -> aliases.putIfAbsent(alias, ref));
        }
        for (Subcategory sub : subcategoryRepository.findAll()) {
            Category parent = categories.get(sub.getCategoryId());
            String name = sub.getSubcategoryName().toLowerCase(Locale.ROOT);
            if (parent == null || AMBIGUOUS_ALIASES.contains(name)) {
                continue;
            }
            addAlias(aliases, name, new CategoryRef(parent.getId(), parent.getCategoryName(), sub.getId(), sub.getSubcategoryName()));
        }
        return new Vocabulary(aliases, queries.merchants(userId));
    }

    private static void addAlias(Map<String, CategoryRef> aliases, String name, CategoryRef ref) {
        aliases.putIfAbsent(name, ref);
        aliases.putIfAbsent(name.endsWith("s") ? name.substring(0, name.length() - 1) : name + "s", ref);
    }

    private YearMonth contextMonth(AssistantContext context, YearMonth latest) {
        if (context == null || (context.selectedMonth() == null && context.selectedYear() == null)) {
            return latest;
        }
        Integer month = null;
        if (context.selectedMonth() != null) {
            String value = context.selectedMonth().trim();
            if (value.matches("\\d{1,2}")) {
                month = Integer.parseInt(value);
            } else {
                for (int m = 1; m <= 12; m++) {
                    String name = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    if (name.equalsIgnoreCase(value) || name.substring(0, 3).equalsIgnoreCase(value)) {
                        month = m;
                    }
                }
            }
            if (month == null || month < 1 || month > 12) {
                throw ApiException.badRequest("INVALID_REQUEST", "context.selectedMonth must be a month name or 1-12");
            }
        }
        YearMonth resolved;
        if (month == null) {
            resolved = YearMonth.of(context.selectedYear(), latest.getMonth());
        } else if (context.selectedYear() == null) {
            resolved = YearMonth.of(latest.getYear(), month);
            if (resolved.isAfter(latest)) {
                resolved = resolved.minusYears(1);
            }
        } else {
            resolved = YearMonth.of(context.selectedYear(), month);
        }
        return resolved.isAfter(latest) ? latest : resolved;
    }

    private YearMonth singleMonth(Ask ask) {
        return ask.parsed().months().isEmpty() ? ask.reference() : ask.parsed().months().get(0);
    }

    private Window window(UUID userId, YearMonth month) {
        LocalDate end = periods.effectiveEnd(userId, month);
        return new Window(month, month.atDay(1), end, end.isBefore(month.atEndOfMonth()));
    }

    private List<Named> categoryParts(UUID userId, LocalDate start, LocalDate end) {
        return aggregates.spendingByCategory(userId, start, end).stream()
                .map(c -> new Named(c.categoryId(), c.categoryName(), c.amount())).toList();
    }

    private List<Named> merchantParts(UUID userId, LocalDate start, LocalDate end, UUID categoryId) {
        return aggregates.topMerchants(userId, start, end, categoryId, 100).stream()
                .map(m -> new Named(m.merchantId(), m.merchantName(), m.amount())).toList();
    }

    private Named categoryAmount(UUID userId, CategoryRef category, LocalDate start, LocalDate end) {
        if (category.subcategoryId() != null) {
            return aggregates.spendingBySubcategory(userId, category.id(), start, end).stream()
                    .filter(s -> category.subcategoryId().equals(s.subcategoryId()))
                    .map(s -> new Named(s.subcategoryId(), s.subcategoryName(), s.amount()))
                    .findFirst().orElse(new Named(null, category.subcategoryName(), BigDecimal.ZERO));
        }
        return aggregates.spendingByCategory(userId, start, end).stream()
                .filter(c -> category.id().equals(c.categoryId()))
                .map(c -> new Named(c.categoryId(), c.categoryName(), c.amount()))
                .findFirst().orElse(new Named(null, category.name(), BigDecimal.ZERO));
    }

    private long countFor(UUID userId, CategoryRef category, Window w) {
        if (category.subcategoryId() != null) {
            return aggregates.spendingBySubcategory(userId, category.id(), w.start(), w.end()).stream()
                    .filter(s -> category.subcategoryId().equals(s.subcategoryId()))
                    .mapToLong(SubcategoryAmount::count).sum();
        }
        return aggregates.spendingByCategory(userId, w.start(), w.end()).stream()
                .filter(c -> category.id().equals(c.categoryId()))
                .mapToLong(CategoryAmount::count).sum();
    }

    /** Per-name changes between two periods, largest absolute change first. */
    private static List<Finding> deltas(List<Named> now, List<Named> before, boolean merchants) {
        Map<String, BigDecimal> nowByName = now.stream().collect(Collectors.toMap(Named::name, Named::amount, BigDecimal::add, LinkedHashMap::new));
        Map<String, BigDecimal> beforeByName = before.stream().collect(Collectors.toMap(Named::name, Named::amount, BigDecimal::add));
        Set<String> names = new java.util.LinkedHashSet<>(nowByName.keySet());
        names.addAll(beforeByName.keySet());
        return names.stream()
                .map(name -> {
                    BigDecimal current = nowByName.getOrDefault(name, BigDecimal.ZERO);
                    BigDecimal previous = beforeByName.getOrDefault(name, BigDecimal.ZERO);
                    return Finding.comparison(merchants ? null : name, merchants ? name : null, Money.scale(current),
                            Money.scale(previous), Money.scale(current.subtract(previous)), Money.percentChange(current, previous));
                })
                .sorted(Comparator.comparing((Finding f) -> f.change().abs()).reversed())
                .toList();
    }

    private static String changeText(Finding finding) {
        return finding.changePercentage() == null ? "new" : signedPercent(finding.changePercentage());
    }

    private static String name(Finding finding) {
        return finding.category() != null ? finding.category() : finding.merchant();
    }

    private static UUID idOf(List<Named> parts, String name) {
        return parts.stream().filter(p -> name.equals(p.name())).map(Named::id).findFirst().orElse(null);
    }

    private static Filter categoryFilter(CategoryRef category) {
        return category == null ? Filter.NONE : new Filter(category.id(), category.subcategoryId(), null);
    }

    private static BigDecimal sum(List<Named> parts) {
        return parts.stream().map(Named::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String displayName(RelatedTransaction transaction) {
        return transaction.merchant() != null ? transaction.merchant() : transaction.description();
    }

    private static String joinWithAnd(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /** "September" in the current year, "September 2025" otherwise. */
    private static String label(YearMonth month, Ask ask) {
        return month.getYear() == ask.latest().getYear() ? monthName(month) : periodLabel(month);
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String periodLabel(YearMonth month) {
        return monthName(month) + " " + month.getYear();
    }
}
