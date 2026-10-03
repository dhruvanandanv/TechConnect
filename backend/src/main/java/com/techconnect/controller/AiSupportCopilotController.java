package com.techconnect.controller;

import com.techconnect.dto.copilot.CopilotAnswerRequest;
import com.techconnect.dto.copilot.CopilotAnswerResponse;
import com.techconnect.service.AiSupportCopilotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the RAG-based AI Support Copilot endpoint.
 * Protected by JWT authentication and RBAC.
 */
@RestController
@RequestMapping("/api/ai/copilot")
@RequiredArgsConstructor
@Slf4j
public class AiSupportCopilotController {

    private final AiSupportCopilotService copilotService;

    /**
     * POST /api/ai/copilot/answer
     * Synthesizes an advisory IT support answer grounded in the TechConnect Knowledge Base.
     */
    @PostMapping("/answer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CopilotAnswerResponse> answerSupportQuestion(
            @Valid @RequestBody CopilotAnswerRequest request,
            Authentication authentication) {
        log.debug("Received copilot answer request from user: {}", authentication.getName());
        CopilotAnswerResponse response = copilotService.answerSupportQuestion(request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
