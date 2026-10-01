package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketSummaryResponse {

    private Long id;
    private String title;
    private TicketCategory category;
    private Priority priority;
    private TicketStatus status;

    private Long requesterId;
    private String requesterName;
    private String requesterEmail;

    private Long assignedEngineerId;
    private String assignedEngineerName;

    private String teamName;
    private String departmentName;

    private LocalDateTime slaDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
