package com.techconnect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiting filter for abuse prevention.
 * Protects expensive AI Copilot, AI Resolution Assistant, and semantic search operations.
 * Exposes configurable limits with HTTP 429 Too Many Requests enforcement.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${techconnect.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${techconnect.rate-limit.ai-rpm:20}")
    private int aiRpm;

    @Value("${techconnect.rate-limit.search-rpm:60}")
    private int searchRpm;

    @Value("${techconnect.rate-limit.general-rpm:120}")
    private int generalRpm;

    // Map of (clientKey + ":" + category) -> Deque of request timestamps in milliseconds
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Whitelist health checks and public static probes
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        String category = resolveCategory(path);
        int maxRpm = resolveMaxRequestsPerMinute(category);

        String bucketKey = clientKey + ":" + category;
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L; // 1-minute sliding window

        boolean allowed;
        int remaining;

        synchronized (requestWindows) {
            Deque<Long> timestamps = requestWindows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());

            // Evict expired timestamps outside the 1-minute sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRpm) {
                timestamps.addLast(now);
                allowed = true;
                remaining = maxRpm - timestamps.size();
            } else {
                allowed = false;
                remaining = 0;
            }
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRpm));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));

        if (!allowed) {
            log.warn("Rate limit exceeded for clientKey={} on path={} (limit={} rpm)", clientKey, path, maxRpm);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .success(false)
                    .message("Rate limit exceeded. Maximum allowed: " + maxRpm + " requests per minute for this service. Please retry in 60 seconds.")
                    .timestamp(LocalDateTime.now())
                    .path(path)
                    .build();

            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        return path.equals("/api/health")
                || path.startsWith("/actuator/")
                || path.equals("/error");
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveCategory(String path) {
        if (path.startsWith("/api/ai/")) {
            return "AI";
        }
        if (path.startsWith("/api/knowledge/search") || path.startsWith("/api/knowledge/vector")) {
            return "SEARCH";
        }
        return "GENERAL";
    }

    private int resolveMaxRequestsPerMinute(String category) {
        return switch (category) {
            case "AI" -> aiRpm;
            case "SEARCH" -> searchRpm;
            default -> generalRpm;
        };
    }

    /**
     * Resets in-memory tracking structures for integration testing.
     */
    public void reset() {
        synchronized (requestWindows) {
            requestWindows.clear();
        }
    }
}
