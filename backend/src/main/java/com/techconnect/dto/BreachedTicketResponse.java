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
public class BreachedTicketResponse {

    private Long ticketId;
    private String title;
    private Priority priority;
    private TicketStatus status;

    private LocalDateTime responseDeadline;
    private LocalDateTime resolutionDeadline;

    private SlaStatus responseStatus;
    private SlaStatus resolutionStatus;

    private Long remainingResolutionMinutes;
    private LocalDateTime breachedAt;
    private String breachType; // "RESPONSE", "RESOLUTION", or "BOTH"
}
