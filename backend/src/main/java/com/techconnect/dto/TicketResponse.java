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
public class TicketResponse {

    private Long id;
    private String title;
    private String description;
    private TicketCategory category;
    private Priority priority;
    private TicketStatus status;

    private UserResponse requester;
    private UserResponse assignedEngineer;
    private Long teamId;
    private String teamName;
    private Long departmentId;
    private String departmentName;

    private LocalDateTime slaDeadline;
    private LocalDateTime resolvedAt;
    private String resolutionDescription;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
