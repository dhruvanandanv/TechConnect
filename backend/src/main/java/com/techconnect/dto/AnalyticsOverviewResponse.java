package com.techconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewResponse {

    // Ticket Status Counts
    private long totalTickets;
    private long openTickets;
    private long assignedTickets;
    private long inProgressTickets;
    private long waitingForUserTickets;
    private long resolvedTickets;
    private long closedTickets;
    private long escalatedTickets;

    // Distributions
    private Map<String, Long> priorityDistribution;
    private Map<String, Long> categoryDistribution;
    private Map<String, Long> statusDistribution;

    // Resolution Time Performance
    private double averageResolutionTimeHours;
    private long totalResolvedTicketsEvaluated;

    // SLA Performance
    private double slaCompliancePercentage;
    private long slaOnTrackCount;
    private long slaAtRiskCount;
    private long slaBreachedCount;
    private long responseSlaMetCount;
    private long responseSlaBreachedCount;
    private long resolutionSlaMetCount;
    private long resolutionSlaBreachedCount;

    // Engineer Workload
    private List<EngineerWorkloadDto> engineerWorkloads;

    // Ticket Trend (Daily over time)
    private List<TicketTrendDto> ticketTrends;

    // Knowledge Base Overview
    private long totalKnowledgeArticles;
    private long publishedKnowledgeArticles;
    private long totalKnowledgeArticleViews;
    private long totalKnowledgeArticleHelpfulVotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngineerWorkloadDto {
        private Long engineerId;
        private String name;
        private String email;
        private long activeTicketsCount;
        private long resolvedTicketsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketTrendDto {
        private String date; // YYYY-MM-DD
        private long createdCount;
        private long resolvedCount;
    }
}
