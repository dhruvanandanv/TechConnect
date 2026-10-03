package com.techconnect.service;

import com.techconnect.dto.resolution.ResolutionSuggestionRequest;
import com.techconnect.dto.resolution.ResolutionSuggestionResponse;

/**
 * Service contract for the AI Engineer Resolution Assistant.
 * Generates grounded resolution suggestions for support engineers
 * using active ticket context, knowledge articles, and historical resolved tickets.
 */
public interface AiResolutionAssistantService {

    /**
     * Generates a grounded resolution suggestion for the specified ticket.
     * Enforces strict RBAC (Engineers, Managers, Admins only) and IDOR view permissions.
     *
     * @param ticketId The target ticket ID
     * @param request Optional tuning parameters (topK, minSimilarity)
     * @param currentUserEmail Authenticated engineer/manager/admin email
     * @return Structured resolution suggestion with steps, citations, and similar tickets
     */
    ResolutionSuggestionResponse generateResolutionSuggestion(
            Long ticketId,
            ResolutionSuggestionRequest request,
            String currentUserEmail
    );
}
