package com.techconnect.service.impl;

import com.techconnect.dto.BreachedTicketResponse;
import com.techconnect.dto.SlaSummaryResponse;
import com.techconnect.dto.TicketSlaResponse;
import com.techconnect.entity.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.SlaStatus;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.exception.ResourceNotFoundException;
import com.techconnect.exception.SlaPolicyNotFoundException;
import com.techconnect.exception.TicketAccessDeniedException;
import com.techconnect.exception.TicketNotFoundException;
import com.techconnect.repository.AuditLogRepository;
import com.techconnect.repository.SLARepository;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlaServiceImpl implements SlaService {

    private final SLARepository slaRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${sla.at-risk-threshold-percentage:0.20}")
    private double atRiskThresholdPercentage;

    @Override
    @Transactional
    public void applySlaToNewTicket(Ticket ticket) {
        Priority priority = ticket.getPriority();
        SLA sla = slaRepository.findByPriority(priority)
                .orElseThrow(() -> new SlaPolicyNotFoundException("No SLA policy configured for priority: " + priority));

        ticket.setSla(sla);

        LocalDateTime baseTime = ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now();
        ticket.setResponseDeadline(baseTime.plusHours(sla.getResponseTimeHours()));
        ticket.setSlaDeadline(baseTime.plusHours(sla.getResolutionTimeHours()));

        recordAuditLog("SLA_ASSIGNED", "Ticket", ticket.getId(), ticket.getCreatedBy(),
                String.format("Assigned %s SLA policy (Response: %dh, Resolution: %dh)",
                        priority, sla.getResponseTimeHours(), sla.getResolutionTimeHours()));

        log.debug("Applied SLA {} to ticket (Response DL: {}, Resolution DL: {})",
                priority, ticket.getResponseDeadline(), ticket.getSlaDeadline());
    }

    @Override
    @Transactional
    public void recordResponseMilestone(Ticket ticket, User actor) {
        if (ticket.getRespondedAt() != null) {
            return; // Already responded
        }

        if (actor == null || actor.getRole() == null) {
            return;
        }

        RoleName role = actor.getRole().getName();
        // Only operational actions by IT staff count as response
        if (role != RoleName.ROLE_ENGINEER && role != RoleName.ROLE_MANAGER && role != RoleName.ROLE_ADMIN) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.setRespondedAt(now);

        boolean breached = ticket.getResponseDeadline() != null && now.isAfter(ticket.getResponseDeadline());
        if (breached) {
            recordAuditLog("RESPONSE_BREACHED", "Ticket", ticket.getId(), actor,
                    String.format("Response recorded at %s, exceeding deadline %s", now, ticket.getResponseDeadline()));
            log.warn("Ticket #{} Response SLA breached at {}", ticket.getId(), now);
        } else {
            recordAuditLog("RESPONSE_COMPLETED", "Ticket", ticket.getId(), actor,
                    String.format("Response milestone completed at %s within deadline %s", now, ticket.getResponseDeadline()));
            log.info("Ticket #{} Response SLA completed at {}", ticket.getId(), now);
        }
    }

    @Override
    @Transactional
    public void handleStatusTransition(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, User actor) {
        LocalDateTime now = LocalDateTime.now();

        // 1. First response on transitioning to IN_PROGRESS (if not already responded)
        if (newStatus == TicketStatus.IN_PROGRESS && ticket.getRespondedAt() == null) {
            recordResponseMilestone(ticket, actor);
        }

        // 2. Pause when entering WAITING_FOR_USER
        if (newStatus == TicketStatus.WAITING_FOR_USER) {
            if (ticket.getSlaPausedAt() == null) {
                ticket.setSlaPausedAt(now);
                recordAuditLog("SLA_PAUSED", "Ticket", ticket.getId(), actor,
                        "SLA clock paused while ticket is WAITING_FOR_USER");
                log.info("Ticket #{} SLA clock paused at {}", ticket.getId(), now);
            }
        }

        // 3. Resume when leaving WAITING_FOR_USER
        if (oldStatus == TicketStatus.WAITING_FOR_USER && newStatus != TicketStatus.WAITING_FOR_USER) {
            if (ticket.getSlaPausedAt() != null) {
                Duration pausedDuration = Duration.between(ticket.getSlaPausedAt(), now);
                long pausedMinutes = Math.max(0, pausedDuration.toMinutes());

                long totalPaused = (ticket.getTotalPausedDurationMinutes() != null ? ticket.getTotalPausedDurationMinutes() : 0L) + pausedMinutes;
                ticket.setTotalPausedDurationMinutes(totalPaused);

                // Extend deadlines by paused duration
                if (ticket.getSlaDeadline() != null) {
                    ticket.setSlaDeadline(ticket.getSlaDeadline().plus(pausedDuration));
                }
                if (ticket.getResponseDeadline() != null && ticket.getRespondedAt() == null) {
                    ticket.setResponseDeadline(ticket.getResponseDeadline().plus(pausedDuration));
                }

                ticket.setSlaPausedAt(null);
                recordAuditLog("SLA_RESUMED", "Ticket", ticket.getId(), actor,
                        String.format("SLA clock resumed after %d minutes pause. Deadlines extended.", pausedMinutes));
                log.info("Ticket #{} SLA clock resumed at {}, paused for {} minutes", ticket.getId(), now, pausedMinutes);
            }
        }

        // 4. Resolution milestone on entering RESOLVED
        if (newStatus == TicketStatus.RESOLVED) {
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(now);
            }
            boolean breached = ticket.getSlaDeadline() != null && ticket.getResolvedAt().isAfter(ticket.getSlaDeadline());
            if (breached) {
                recordAuditLog("RESOLUTION_BREACHED", "Ticket", ticket.getId(), actor,
                        String.format("Ticket resolved at %s, exceeding resolution deadline %s", ticket.getResolvedAt(), ticket.getSlaDeadline()));
                log.warn("Ticket #{} Resolution SLA breached at {}", ticket.getId(), ticket.getResolvedAt());
            } else {
                recordAuditLog("RESOLUTION_COMPLETED", "Ticket", ticket.getId(), actor,
                        String.format("Ticket resolved at %s within resolution deadline %s", ticket.getResolvedAt(), ticket.getSlaDeadline()));
                log.info("Ticket #{} Resolution SLA completed successfully at {}", ticket.getId(), ticket.getResolvedAt());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSlaResponse getTicketSla(Long ticketId, String currentUserEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + ticketId));

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + currentUserEmail));

        assertCanViewTicket(ticket, currentUser);

        return calculateTicketSla(ticket, LocalDateTime.now());
    }

    @Override
    public TicketSlaResponse calculateTicketSla(Ticket ticket, LocalDateTime now) {
        // Total minutes for policy thresholds
        long totalResponseMinutes = calculateTotalResponseMinutes(ticket);
        long totalResolutionMinutes = calculateTotalResolutionMinutes(ticket);

        // 1. Response status & remaining minutes
        SlaStatus responseStatus;
        long remainingResponseMinutes = 0L;

        if (ticket.getRespondedAt() != null) {
            if (ticket.getResponseDeadline() != null && ticket.getRespondedAt().isAfter(ticket.getResponseDeadline())) {
                responseStatus = SlaStatus.BREACHED;
            } else {
                responseStatus = SlaStatus.COMPLETED;
            }
        } else {
            if (ticket.getStatus() == TicketStatus.WAITING_FOR_USER || ticket.getSlaPausedAt() != null) {
                responseStatus = SlaStatus.PAUSED;
                if (ticket.getResponseDeadline() != null) {
                    remainingResponseMinutes = Math.max(0, Duration.between(now, ticket.getResponseDeadline()).toMinutes());
                }
            } else if (ticket.getResponseDeadline() != null && now.isAfter(ticket.getResponseDeadline())) {
                responseStatus = SlaStatus.BREACHED;
            } else if (ticket.getResponseDeadline() != null) {
                remainingResponseMinutes = Math.max(0, Duration.between(now, ticket.getResponseDeadline()).toMinutes());
                double threshold = totalResponseMinutes * atRiskThresholdPercentage;
                if (remainingResponseMinutes <= threshold) {
                    responseStatus = SlaStatus.AT_RISK;
                } else {
                    responseStatus = SlaStatus.ON_TRACK;
                }
            } else {
                responseStatus = SlaStatus.ON_TRACK;
            }
        }

        // 2. Resolution status & remaining minutes
        SlaStatus resolutionStatus;
        long remainingResolutionMinutes = 0L;

        if (ticket.getResolvedAt() != null) {
            if (ticket.getSlaDeadline() != null && ticket.getResolvedAt().isAfter(ticket.getSlaDeadline())) {
                resolutionStatus = SlaStatus.BREACHED;
            } else {
                resolutionStatus = SlaStatus.COMPLETED;
            }
        } else {
            if (ticket.getStatus() == TicketStatus.WAITING_FOR_USER || ticket.getSlaPausedAt() != null) {
                resolutionStatus = SlaStatus.PAUSED;
                if (ticket.getSlaDeadline() != null) {
                    remainingResolutionMinutes = Math.max(0, Duration.between(now, ticket.getSlaDeadline()).toMinutes());
                }
            } else if (ticket.getSlaDeadline() != null && now.isAfter(ticket.getSlaDeadline())) {
                resolutionStatus = SlaStatus.BREACHED;
            } else if (ticket.getSlaDeadline() != null) {
                remainingResolutionMinutes = Math.max(0, Duration.between(now, ticket.getSlaDeadline()).toMinutes());
                double threshold = totalResolutionMinutes * atRiskThresholdPercentage;
                if (remainingResolutionMinutes <= threshold) {
                    resolutionStatus = SlaStatus.AT_RISK;
                } else {
                    resolutionStatus = SlaStatus.ON_TRACK;
                }
            } else {
                resolutionStatus = SlaStatus.ON_TRACK;
            }
        }

        // 3. Overall status determination
        SlaStatus overallStatus;
        if (responseStatus == SlaStatus.BREACHED || resolutionStatus == SlaStatus.BREACHED) {
            overallStatus = SlaStatus.BREACHED;
        } else if (resolutionStatus == SlaStatus.COMPLETED) {
            overallStatus = SlaStatus.COMPLETED;
        } else if (responseStatus == SlaStatus.PAUSED || resolutionStatus == SlaStatus.PAUSED) {
            overallStatus = SlaStatus.PAUSED;
        } else if (responseStatus == SlaStatus.AT_RISK || resolutionStatus == SlaStatus.AT_RISK) {
            overallStatus = SlaStatus.AT_RISK;
        } else {
            overallStatus = SlaStatus.ON_TRACK;
        }

        return TicketSlaResponse.builder()
                .ticketId(ticket.getId())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .responseDeadline(ticket.getResponseDeadline())
                .resolutionDeadline(ticket.getSlaDeadline())
                .responseStatus(responseStatus)
                .resolutionStatus(resolutionStatus)
                .overallStatus(overallStatus)
                .respondedAt(ticket.getRespondedAt())
                .resolvedAt(ticket.getResolvedAt())
                .remainingResponseMinutes(remainingResponseMinutes)
                .remainingResolutionMinutes(remainingResolutionMinutes)
                .isPaused(ticket.getSlaPausedAt() != null || ticket.getStatus() == TicketStatus.WAITING_FOR_USER)
                .slaPausedAt(ticket.getSlaPausedAt())
                .totalPausedDurationMinutes(ticket.getTotalPausedDurationMinutes() != null ? ticket.getTotalPausedDurationMinutes() : 0L)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BreachedTicketResponse> getBreachedTickets() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> allTickets = ticketRepository.findAll();
        List<BreachedTicketResponse> breached = new ArrayList<>();

        for (Ticket ticket : allTickets) {
            TicketSlaResponse sla = calculateTicketSla(ticket, now);
            boolean respBreached = sla.getResponseStatus() == SlaStatus.BREACHED;
            boolean resolBreached = sla.getResolutionStatus() == SlaStatus.BREACHED;

            if (respBreached || resolBreached) {
                String breachType;
                LocalDateTime breachedAt;
                if (respBreached && resolBreached) {
                    breachType = "BOTH";
                    breachedAt = ticket.getResponseDeadline();
                } else if (respBreached) {
                    breachType = "RESPONSE";
                    breachedAt = ticket.getResponseDeadline();
                } else {
                    breachType = "RESOLUTION";
                    breachedAt = ticket.getSlaDeadline();
                }

                breached.add(BreachedTicketResponse.builder()
                        .ticketId(ticket.getId())
                        .title(ticket.getTitle())
                        .priority(ticket.getPriority())
                        .status(ticket.getStatus())
                        .responseDeadline(ticket.getResponseDeadline())
                        .resolutionDeadline(ticket.getSlaDeadline())
                        .responseStatus(sla.getResponseStatus())
                        .resolutionStatus(sla.getResolutionStatus())
                        .remainingResolutionMinutes(sla.getRemainingResolutionMinutes())
                        .breachedAt(breachedAt)
                        .breachType(breachType)
                        .build());
            }
        }

        return breached;
    }

    @Override
    @Transactional(readOnly = true)
    public SlaSummaryResponse getSlaSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> allTickets = ticketRepository.findAll();

        long totalActiveTickets = 0;
        long onTrack = 0;
        long atRisk = 0;
        long breached = 0;
        long responseSlaMet = 0;
        long responseSlaBreached = 0;
        long resolutionSlaMet = 0;
        long resolutionSlaBreached = 0;

        for (Ticket ticket : allTickets) {
            TicketSlaResponse sla = calculateTicketSla(ticket, now);

            if (ticket.getStatus() != TicketStatus.CLOSED) {
                totalActiveTickets++;
                switch (sla.getOverallStatus()) {
                    case ON_TRACK -> onTrack++;
                    case AT_RISK -> atRisk++;
                    case BREACHED -> breached++;
                    case PAUSED -> onTrack++;
                    case COMPLETED -> onTrack++;
                }
            }

            // Milestone aggregates
            if (sla.getResponseStatus() == SlaStatus.COMPLETED) {
                responseSlaMet++;
            } else if (sla.getResponseStatus() == SlaStatus.BREACHED) {
                responseSlaBreached++;
            }

            if (sla.getResolutionStatus() == SlaStatus.COMPLETED) {
                resolutionSlaMet++;
            } else if (sla.getResolutionStatus() == SlaStatus.BREACHED) {
                resolutionSlaBreached++;
            }
        }

        return SlaSummaryResponse.builder()
                .totalActiveTickets(totalActiveTickets)
                .onTrack(onTrack)
                .atRisk(atRisk)
                .breached(breached)
                .responseSlaMet(responseSlaMet)
                .responseSlaBreached(responseSlaBreached)
                .resolutionSlaMet(resolutionSlaMet)
                .resolutionSlaBreached(resolutionSlaBreached)
                .build();
    }

    private long calculateTotalResponseMinutes(Ticket ticket) {
        if (ticket.getSla() != null && ticket.getSla().getResponseTimeHours() != null) {
            return ticket.getSla().getResponseTimeHours() * 60L;
        }
        if (ticket.getCreatedAt() != null && ticket.getResponseDeadline() != null) {
            return Math.max(1L, Duration.between(ticket.getCreatedAt(), ticket.getResponseDeadline()).toMinutes());
        }
        return 240L;
    }

    private long calculateTotalResolutionMinutes(Ticket ticket) {
        if (ticket.getSla() != null && ticket.getSla().getResolutionTimeHours() != null) {
            return ticket.getSla().getResolutionTimeHours() * 60L;
        }
        if (ticket.getCreatedAt() != null && ticket.getSlaDeadline() != null) {
            return Math.max(1L, Duration.between(ticket.getCreatedAt(), ticket.getSlaDeadline()).toMinutes());
        }
        return 1440L;
    }

    private void assertCanViewTicket(Ticket ticket, User user) {
        RoleName role = user.getRole().getName();
        if (role == RoleName.ROLE_ADMIN) {
            return;
        }

        if (ticket.getCreatedBy() != null && ticket.getCreatedBy().getId().equals(user.getId())) {
            return;
        }

        if (role == RoleName.ROLE_ENGINEER) {
            if (ticket.getAssignedEngineer() != null && ticket.getAssignedEngineer().getId().equals(user.getId())) {
                return;
            }
            if (ticket.getStatus() == TicketStatus.OPEN && ticket.getAssignedEngineer() == null) {
                return;
            }
            throw new TicketAccessDeniedException("Access denied: You are not assigned to this ticket");
        }

        if (role == RoleName.ROLE_MANAGER) {
            if (user.getTeam() != null && ticket.getAssignedTeam() != null
                    && ticket.getAssignedTeam().getId().equals(user.getTeam().getId())) {
                return;
            }
            if (user.getDepartment() != null && ticket.getDepartment() != null
                    && ticket.getDepartment().getId().equals(user.getDepartment().getId())) {
                return;
            }
            throw new TicketAccessDeniedException("Access denied: Ticket does not belong to your managed team or department");
        }

        throw new TicketAccessDeniedException("Access denied: You do not have permission to view SLA metrics for this ticket");
    }

    private void recordAuditLog(String action, String entityName, Long entityId, User actor, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityName(entityName)
                    .entityId(entityId)
                    .performedBy(actor)
                    .details(details)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist audit log entry for action: {}", action, e);
        }
    }
}
