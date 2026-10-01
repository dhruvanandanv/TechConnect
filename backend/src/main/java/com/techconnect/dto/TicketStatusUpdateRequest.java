package com.techconnect.dto;

import com.techconnect.entity.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketStatusUpdateRequest {

    @NotNull(message = "New status is required")
    private TicketStatus status;

    private String reason;

    private String resolutionDescription;
}
