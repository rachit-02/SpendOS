package com.spendos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the per-endpoint limits from SECURITY.md. Auth endpoints are keyed by client IP; all other
 * endpoints by authenticated user (falling back to IP). Runs after JWT authentication.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String name, String method, String path, boolean prefix, int limit, Duration window, boolean byIp) {
        boolean matches(String requestMethod, String requestPath) {
            boolean pathMatches = prefix ? requestPath.startsWith(path) : requestPath.equals(path);
            return pathMatches && (method == null || method.equalsIgnoreCase(requestMethod));
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/v1/auth/login", false, 5, Duration.ofMinutes(15), true),
            new Rule("register", "POST", "/v1/auth/register", false, 5, Duration.ofHours(1), true),
            new Rule("upload", "POST", "/v1/imports/upload", false, 10, Duration.ofHours(1), false),
            new Rule("tx-read", "GET", "/v1/transactions", true, 100, Duration.ofMinutes(1), false),
            new Rule("tx-write", "POST", "/v1/transactions", true, 50, Duration.ofMinutes(1), false),
            // May call a paid language model; keeps cost and abuse bounded (RISKS_AND_ASSUMPTIONS.md #8).
            new Rule("assistant", "POST", "/v1/assistant/query", false, 30, Duration.ofMinutes(1), false));

    static final Rule GENERAL = new Rule("general", null, "/v1/", true, 1000, Duration.ofHours(1), false);

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || "OPTIONS".equalsIgnoreCase(request.getMethod()) || path(request).equals("/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        String method = request.getMethod();
        Rule specific = RULES.stream().filter(rule -> rule.matches(method, path)).findFirst().orElse(null);
        if (specific != null && !acquire(specific, request, response)) {
            return;
        }
        if (GENERAL.matches(method, path) && !acquire(GENERAL, request, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean acquire(Rule rule, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String client = rule.byIp() ? "ip:" + request.getRemoteAddr() : clientKey(request);
        RateLimiter.Decision decision = rateLimiter.tryAcquire(rule.name() + "|" + client, rule.limit(), rule.window());
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("RATE_LIMITED",
                "Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.",
                Map.of("retryAfterSeconds", decision.retryAfterSeconds())));
        return false;
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return "user:" + userId;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }
}
