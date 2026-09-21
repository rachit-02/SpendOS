package com.spendos.simulations.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.MonthTotals;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Times;
import com.spendos.goals.dto.GoalDtos.GoalResponse;
import com.spendos.goals.service.GoalService;
import com.spendos.simulations.domain.Simulation;
import com.spendos.simulations.dto.SimulationDtos.Comparison;
import com.spendos.simulations.dto.SimulationDtos.Results;
import com.spendos.simulations.dto.SimulationDtos.Scenario;
import com.spendos.simulations.dto.SimulationDtos.SimulationRequest;
import com.spendos.simulations.dto.SimulationDtos.SimulationResponse;
import com.spendos.simulations.repository.SimulationRepository;
import com.spendos.simulations.service.SimulationCalculator.Baseline;
import com.spendos.simulations.service.SimulationCalculator.GoalState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {

    static final int BASELINE_MONTHS = 3;

    private final SimulationRepository repository;
    private final AggregateQueries aggregates;
    private final CategoryService categoryService;
    private final GoalService goalService;
    private final PeriodResolver periods;
    private final ObjectMapper objectMapper;

    public SimulationService(SimulationRepository repository, AggregateQueries aggregates, CategoryService categoryService,
                             GoalService goalService, PeriodResolver periods, ObjectMapper objectMapper) {
        this.repository = repository;
        this.aggregates = aggregates;
        this.categoryService = categoryService;
        this.goalService = goalService;
        this.periods = periods;
        this.objectMapper = objectMapper;
    }

    /** Runs the scenarios against the current baseline and saves them. */
    @Transactional
    public SimulationResponse create(UUID userId, SimulationRequest request) {
        request.scenarios().forEach(s -> {
            if (s.categoryId() != null) {
                categoryService.require(s.categoryId());
            }
        });
        Results results = SimulationCalculator.calculate(baseline(userId), request.scenarios(), goals(userId));
        Simulation simulation = new Simulation();
        simulation.setUserId(userId);
        simulation.setSimulationName(request.simulationName().trim());
        simulation.setScenarios(write(request.scenarios()));
        simulation.setResults(write(results));
        return toResponse(repository.save(simulation));
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SimulationResponse get(UUID userId, UUID id) {
        return toResponse(require(userId, id));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(require(userId, id));
    }

    /** Side by side, re-run against today's baseline so the comparison is fair. */
    @Transactional(readOnly = true)
    public Comparison compare(UUID userId, List<UUID> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 5) {
            throw ApiException.badRequest("INVALID_REQUEST", "Compare between 2 and 5 simulations");
        }
        List<Simulation> found = repository.findByUserIdAndIdIn(userId, ids);
        if (found.size() != ids.size()) {
            throw ApiException.notFound("Simulation not found");
        }
        Baseline baseline = baseline(userId);
        List<GoalState> goals = goals(userId);
        List<SimulationResponse> rerun = found.stream().map(s -> {
            List<Scenario> scenarios = readScenarios(s.getScenarios());
            return new SimulationResponse(s.getId(), s.getSimulationName(), scenarios,
                    SimulationCalculator.calculate(baseline, scenarios, goals), Times.utc(s.getCreatedAt()));
        }).toList();
        UUID best = rerun.stream().max(Comparator.comparing(r -> r.results().annualSavings().after()))
                .map(SimulationResponse::id).orElse(null);
        return new Comparison(rerun, best);
    }

    Baseline baseline(UUID userId) {
        YearMonth current = YearMonth.from(periods.today(userId));
        List<MonthTotals> months = aggregates.monthlyTotals(userId, current.minusMonths(BASELINE_MONTHS),
                        current.minusMonths(1)).stream()
                .filter(m -> m.income().signum() != 0 || m.expense().signum() != 0).toList();
        int count = months.size();
        if (count == 0) {
            return new Baseline(BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), Map.of(), 0);
        }
        BigDecimal income = months.stream().map(MonthTotals::income).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal expense = months.stream().map(MonthTotals::expense).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        Map<UUID, BigDecimal> perCategory = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (CategoryAmount c : aggregates.spendingByCategory(userId, months.get(0).month().atDay(1),
                current.minusMonths(1).atEndOfMonth())) {
            if (c.categoryId() != null) {
                perCategory.put(c.categoryId(), c.amount().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
                names.put(c.categoryId(), c.categoryName());
            }
        }
        return new Baseline(income, expense, perCategory, names, count);
    }

    private List<GoalState> goals(UUID userId) {
        return goalService.list(userId, true, null).stream()
                .map((GoalResponse g) -> new GoalState(g.id(), g.goalName(), g.remainingAmount())).toList();
    }

    private Simulation require(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Simulation not found"));
    }

    private SimulationResponse toResponse(Simulation s) {
        try {
            return new SimulationResponse(s.getId(), s.getSimulationName(), readScenarios(s.getScenarios()),
                    objectMapper.readValue(s.getResults(), Results.class), Times.utc(s.getCreatedAt()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored simulation is unreadable", exception);
        }
    }

    private List<Scenario> readScenarios(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Scenario>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Stored scenarios are unreadable", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
