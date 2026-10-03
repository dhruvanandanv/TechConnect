package com.techconnect.service;

import com.techconnect.dto.copilot.CopilotAnswerRequest;
import com.techconnect.dto.copilot.CopilotAnswerResponse;

/**
 * Service orchestrating RAG-based AI Support Copilot inquiries.
 * Integrates ticket context validation (IDOR protection), RBAC knowledge scoping,
 * and dispatching to Python AI RAG service.
 */
public interface AiSupportCopilotService {

    /**
     * Answers an IT support query grounded in the TechConnect Knowledge Base.
     *
     * @param request user inquiry, optional category, and optional ticketId
     * @param currentUserEmail authenticated user's email
     * @return grounded answer with citations and retrieval metadata
     */
    CopilotAnswerResponse answerSupportQuestion(CopilotAnswerRequest request, String currentUserEmail);
}
