package com.techconnect.service.impl;

import com.techconnect.document.KnowledgeArticle;
import com.techconnect.dto.AnalyticsOverviewResponse;
import com.techconnect.dto.SlaSummaryResponse;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
import com.techconnect.service.AnalyticsService;
import com.techconnect.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SlaService slaService;
    private final KnowledgeArticleRepository knowledgeArticleRepository;

    @Override
    public AnalyticsOverviewResponse getAnalyticsOverview() {
        log.debug("Aggregating full-scale ITSM analytics overview");

        List<Ticket> allTickets = ticketRepository.findAll();
        long totalTickets = allTickets.size();

        // Status counts
        Map<TicketStatus, Long> statusCounts = allTickets.stream()
                .filter(t -> t.getStatus() != null)
                .collect(Collectors.groupingBy(Ticket::getStatus, Collectors.counting()));

        long open = statusCounts.getOrDefault(TicketStatus.OPEN, 0L);
        long assigned = statusCounts.getOrDefault(TicketStatus.ASSIGNED, 0L);
        long inProgress = statusCounts.getOrDefault(TicketStatus.IN_PROGRESS, 0L);
        long waitingForUser = statusCounts.getOrDefault(TicketStatus.WAITING_FOR_USER, 0L);
        long resolved = statusCounts.getOrDefault(TicketStatus.RESOLVED, 0L);
        long closed = statusCounts.getOrDefault(TicketStatus.CLOSED, 0L);
        long escalated = statusCounts.getOrDefault(TicketStatus.ESCALATED, 0L);

        // Priority distribution
        Map<String, Long> priorityDistribution = new LinkedHashMap<>();
        for (Priority priority : Priority.values()) {
            priorityDistribution.put(priority.name(), 0L);
        }
        allTickets.stream()
                .filter(t -> t.getPriority() != null)
                .forEach(t -> priorityDistribution.compute(t.getPriority().name(), (k, v) -> v == null ? 1L : v + 1L));

        // Category distribution
        Map<String, Long> categoryDistribution = new LinkedHashMap<>();
        for (TicketCategory category : TicketCategory.values()) {
            categoryDistribution.put(category.name(), 0L);
        }
        allTickets.stream()
                .filter(t -> t.getCategory() != null)
                .forEach(t -> categoryDistribution.compute(t.getCategory().name(), (k, v) -> v == null ? 1L : v + 1L));

        // Status distribution map
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            statusDistribution.put(status.name(), statusCounts.getOrDefault(status, 0L));
        }

        // Average Resolution Time
        List<Ticket> resolvedTickets = allTickets.stream()
                .filter(t -> t.getResolvedAt() != null && t.getCreatedAt() != null)
                .toList();

        double avgResolutionHours = 0.0;
        if (!resolvedTickets.isEmpty()) {
            double totalHours = resolvedTickets.stream()
                    .mapToDouble(t -> {
                        Duration duration = Duration.between(t.getCreatedAt(), t.getResolvedAt());
                        return Math.max(0.0, duration.toMinutes() / 60.0);
                    })
                    .sum();
            avgResolutionHours = BigDecimal.valueOf(totalHours / resolvedTickets.size())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // SLA Performance
        SlaSummaryResponse slaSummary = slaService.getSlaSummary();
        long totalEvaluated = (slaSummary.getResponseSlaMet() + slaSummary.getResponseSlaBreached()
                + slaSummary.getResolutionSlaMet() + slaSummary.getResolutionSlaBreached());
        long totalMet = slaSummary.getResponseSlaMet() + slaSummary.getResolutionSlaMet();
        double slaCompliance = totalEvaluated > 0
                ? BigDecimal.valueOf(((double) totalMet / totalEvaluated) * 100.0)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 100.0;

        // Engineer Workload
        List<User> engineers = userRepository.findByRoleName(RoleName.ROLE_ENGINEER);
        List<AnalyticsOverviewResponse.EngineerWorkloadDto> engineerWorkloads = new ArrayList<>();
        for (User eng : engineers) {
            long activeCount = allTickets.stream()
                    .filter(t -> t.getAssignedEngineer() != null
                            && Objects.equals(t.getAssignedEngineer().getId(), eng.getId())
                            && t.getStatus() != TicketStatus.RESOLVED
                            && t.getStatus() != TicketStatus.CLOSED)
                    .count();
            long resolvedCount = allTickets.stream()
                    .filter(t -> t.getAssignedEngineer() != null
                            && Objects.equals(t.getAssignedEngineer().getId(), eng.getId())
                            && (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED))
                    .count();

            engineerWorkloads.add(AnalyticsOverviewResponse.EngineerWorkloadDto.builder()
                    .engineerId(eng.getId())
                    .name(eng.getFirstName() + " " + eng.getLastName())
                    .email(eng.getEmail())
                    .activeTicketsCount(activeCount)
                    .resolvedTicketsCount(resolvedCount)
                    .build());
        }

        // Ticket Trends (Daily over the past 14 days)
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<AnalyticsOverviewResponse.TicketTrendDto> trends = new ArrayList<>();

        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String dayStr = day.format(formatter);

            long createdOnDay = allTickets.stream()
                    .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().toLocalDate().equals(day))
                    .count();

            long resolvedOnDay = allTickets.stream()
                    .filter(t -> t.getResolvedAt() != null && t.getResolvedAt().toLocalDate().equals(day))
                    .count();

            trends.add(AnalyticsOverviewResponse.TicketTrendDto.builder()
                    .date(dayStr)
                    .createdCount(createdOnDay)
                    .resolvedCount(resolvedOnDay)
                    .build());
        }

        // Knowledge Base Metrics
        long totalArticles = 0L;
        long publishedArticles = 0L;
        long totalViews = 0L;
        long totalHelpful = 0L;
        try {
            List<KnowledgeArticle> articles = knowledgeArticleRepository.findAll();
            totalArticles = articles.size();
            publishedArticles = articles.stream().filter(a -> a.getStatus() == ArticleStatus.PUBLISHED).count();
            totalViews = articles.stream().mapToLong(a -> a.getViewCount() != null ? a.getViewCount() : 0L).sum();
            totalHelpful = articles.stream().mapToLong(a -> a.getHelpfulCount() != null ? a.getHelpfulCount() : 0L).sum();
        } catch (Exception e) {
            log.warn("Unable to aggregate MongoDB knowledge base metrics: {}", e.getMessage());
        }

        return AnalyticsOverviewResponse.builder()
                .totalTickets(totalTickets)
                .openTickets(open)
                .assignedTickets(assigned)
                .inProgressTickets(inProgress)
                .waitingForUserTickets(waitingForUser)
                .resolvedTickets(resolved)
                .closedTickets(closed)
                .escalatedTickets(escalated)
                .priorityDistribution(priorityDistribution)
                .categoryDistribution(categoryDistribution)
                .statusDistribution(statusDistribution)
                .averageResolutionTimeHours(avgResolutionHours)
                .totalResolvedTicketsEvaluated(resolvedTickets.size())
                .slaCompliancePercentage(slaCompliance)
                .slaOnTrackCount(slaSummary.getOnTrack())
                .slaAtRiskCount(slaSummary.getAtRisk())
                .slaBreachedCount(slaSummary.getBreached())
                .responseSlaMetCount(slaSummary.getResponseSlaMet())
                .responseSlaBreachedCount(slaSummary.getResponseSlaBreached())
                .resolutionSlaMetCount(slaSummary.getResolutionSlaMet())
                .resolutionSlaBreachedCount(slaSummary.getResolutionSlaBreached())
                .engineerWorkloads(engineerWorkloads)
                .ticketTrends(trends)
                .totalKnowledgeArticles(totalArticles)
                .publishedKnowledgeArticles(publishedArticles)
                .totalKnowledgeArticleViews(totalViews)
                .totalKnowledgeArticleHelpfulVotes(totalHelpful)
                .build();
    }
}
