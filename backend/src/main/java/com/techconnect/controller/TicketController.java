package com.techconnect.controller;

import com.techconnect.dto.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketController {

    private final TicketService ticketService;
    private final com.techconnect.service.AiTicketIntelligenceService aiTicketIntelligenceService;

    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResponse> analyzeTicket(
            @Valid @RequestBody AiAnalysisRequest request,
            Authentication authentication) {
        log.info("Received ticket AI analysis request by user '{}'", authentication.getName());
        AiAnalysisResponse response = aiTicketIntelligenceService.analyzeTicket(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {
        TicketResponse response = ticketService.createTicket(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<Page<TicketSummaryResponse>> getMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) TicketCategory category,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<TicketSummaryResponse> response = ticketService.getMyTickets(
                authentication.getName(), status, priority, category, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(
            @PathVariable Long id,
            Authentication authentication) {
        TicketResponse response = ticketService.getTicketById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            Authentication authentication) {
        TicketResponse response = ticketService.updateTicket(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateRequest request,
            Authentication authentication) {
        TicketResponse response = ticketService.updateTicketStatus(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/assignment")
    public ResponseEntity<TicketResponse> assignTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketAssignmentRequest request,
            Authentication authentication) {
        TicketResponse response = ticketService.assignTicket(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketCommentResponse> addComment(
            @PathVariable Long id,
            @Valid @RequestBody TicketCommentRequest request,
            Authentication authentication) {
        TicketCommentResponse response = ticketService.addComment(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<TicketCommentResponse>> getComments(
            @PathVariable Long id,
            Authentication authentication) {
        List<TicketCommentResponse> response = ticketService.getComments(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<TicketStatusHistoryResponse>> getStatusHistory(
            @PathVariable Long id,
            Authentication authentication) {
        List<TicketStatusHistoryResponse> response = ticketService.getStatusHistory(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<TicketAssignmentResponse>> getAssignmentHistory(
            @PathVariable Long id,
            Authentication authentication) {
        List<TicketAssignmentResponse> response = ticketService.getAssignmentHistory(id, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
