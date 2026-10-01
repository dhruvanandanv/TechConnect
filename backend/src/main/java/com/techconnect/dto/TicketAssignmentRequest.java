package com.techconnect.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAssignmentRequest {

    @NotNull(message = "Engineer ID is required")
    private Long engineerId;

    private Long teamId;

    private String notes;
}
