package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketAssignmentResponse {

    private Long id;
    private Long ticketId;
    private Long assignedEngineerId;
    private String assignedEngineerName;
    private Long assignedById;
    private String assignedByName;
    private Long assignedTeamId;
    private String assignedTeamName;
    private String notes;
    private LocalDateTime assignedAt;
}
