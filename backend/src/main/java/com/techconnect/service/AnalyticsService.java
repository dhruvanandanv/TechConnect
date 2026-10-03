package com.techconnect.service;

import com.techconnect.dto.AnalyticsOverviewResponse;

public interface AnalyticsService {

    /**
     * Aggregates comprehensive ITSM analytics across tickets, SLAs, engineer workloads,
     * historical resolution times, trends, and knowledge base performance.
     *
     * @return Real-time aggregated AnalyticsOverviewResponse.
     */
    AnalyticsOverviewResponse getAnalyticsOverview();
}
