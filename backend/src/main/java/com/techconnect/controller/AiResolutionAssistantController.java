package com.techconnect.controller;

import com.techconnect.dto.resolution.ResolutionSuggestionRequest;
import com.techconnect.dto.resolution.ResolutionSuggestionResponse;
import com.techconnect.service.AiResolutionAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the Phase 13 AI Engineer Resolution Assistant endpoint.
 * Protected by JWT authentication and role-based access control (Engineer, Manager, Admin).
 * Rejects employee roles and enforces ticket visibility / IDOR verification.
 */
@RestController
@RequestMapping("/api/ai/tickets")
@RequiredArgsConstructor
@Slf4j
public class AiResolutionAssistantController {

    private final AiResolutionAssistantService resolutionAssistantService;

    /**
     * POST /api/ai/tickets/{ticketId}/resolution-suggestion
     * Generates a grounded, advisory troubleshooting and resolution suggestion
     * combining current ticket context, published knowledge base articles, and
     * similar resolved historical tickets.
     *
     * @param ticketId       Target ticket ID
     * @param request        Optional parameters (topK, minSimilarity, includeSimilarTickets)
     * @param authentication Current user authentication context
     * @return Structured ResolutionSuggestionResponse
     */
    @PostMapping("/{ticketId}/resolution-suggestion")
    @PreAuthorize("hasAnyRole('ENGINEER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ResolutionSuggestionResponse> generateResolutionSuggestion(
            @PathVariable Long ticketId,
            @Valid @RequestBody(required = false) ResolutionSuggestionRequest request,
            Authentication authentication) {

        if (request == null) {
            request = ResolutionSuggestionRequest.builder().build();
        }

        String username = authentication != null ? authentication.getName() : "anonymous";
        log.info("Generating AI resolution suggestion for ticketId={} requested by user={}", ticketId, username);

        ResolutionSuggestionResponse response = resolutionAssistantService.generateResolutionSuggestion(
                ticketId, request, username);

        return ResponseEntity.ok(response);
    }
}
