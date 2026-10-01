package com.techconnect.service;

import com.techconnect.dto.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, String currentUserEmail);

    Page<TicketSummaryResponse> getMyTickets(
            String currentUserEmail,
            TicketStatus status,
            Priority priority,
            TicketCategory category,
            Pageable pageable
    );

    TicketResponse getTicketById(Long id, String currentUserEmail);

    TicketResponse updateTicket(Long id, UpdateTicketRequest request, String currentUserEmail);

    TicketResponse updateTicketStatus(Long id, TicketStatusUpdateRequest request, String currentUserEmail);

    TicketResponse assignTicket(Long id, TicketAssignmentRequest request, String currentUserEmail);

    TicketCommentResponse addComment(Long id, TicketCommentRequest request, String currentUserEmail);

    List<TicketCommentResponse> getComments(Long id, String currentUserEmail);

    List<TicketStatusHistoryResponse> getStatusHistory(Long id, String currentUserEmail);

    List<TicketAssignmentResponse> getAssignmentHistory(Long id, String currentUserEmail);
}
