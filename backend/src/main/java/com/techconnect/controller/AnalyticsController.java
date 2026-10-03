package com.techconnect.controller;

import com.techconnect.dto.AnalyticsOverviewResponse;
import com.techconnect.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Retrieves aggregated ITSM platform analytics including ticket volume,
     * status and priority distributions, SLA compliance, engineer workload,
     * and resolution time metrics.
     */
    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnalyticsOverviewResponse> getAnalyticsOverview() {
        log.debug("GET /api/analytics/overview requested");
        AnalyticsOverviewResponse response = analyticsService.getAnalyticsOverview();
        return ResponseEntity.ok(response);
    }
}
