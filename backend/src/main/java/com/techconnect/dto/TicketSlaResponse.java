package com.techconnect.dto;

import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.SlaStatus;
import com.techconnect.entity.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaResponse {

    private Long ticketId;
    private Priority priority;
    private TicketStatus status;

    private LocalDateTime responseDeadline;
    private LocalDateTime resolutionDeadline;

    private SlaStatus responseStatus;
    private SlaStatus resolutionStatus;
    private SlaStatus overallStatus;

    private LocalDateTime respondedAt;
    private LocalDateTime resolvedAt;

    private Long remainingResponseMinutes;
    private Long remainingResolutionMinutes;

    private Boolean isPaused;
    private LocalDateTime slaPausedAt;
    private Long totalPausedDurationMinutes;
}
