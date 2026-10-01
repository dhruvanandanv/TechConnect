package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techconnect.entity.enums.TicketStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketStatusHistoryResponse {

    private Long id;
    private Long ticketId;
    private TicketStatus oldStatus;
    private TicketStatus newStatus;
    private Long changedById;
    private String changedByName;
    private String changeReason;
    private LocalDateTime changedAt;
}
