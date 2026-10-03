package com.techconnect.service.impl;

import com.techconnect.client.AiSupportCopilotClient;
import com.techconnect.document.KnowledgeArticle;
import com.techconnect.dto.TicketResponse;
import com.techconnect.dto.copilot.CopilotAnswerRequest;
import com.techconnect.dto.copilot.CopilotAnswerResponse;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.exception.ResourceNotFoundException;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
import com.techconnect.service.AiSupportCopilotService;
import com.techconnect.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Production implementation of the RAG AI Support Copilot Service.
 * Coordinates IDOR-protected ticket retrieval, role-based knowledge filtering,
 * and calls the Python RAG engine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiSupportCopilotServiceImpl implements AiSupportCopilotService {

    private final AiSupportCopilotClient copilotClient;
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final KnowledgeArticleRepository articleRepository;

    @Override
    @Transactional(readOnly = true)
    public CopilotAnswerResponse answerSupportQuestion(CopilotAnswerRequest request, String currentUserEmail) {
        log.info("Processing Copilot inquiry from user '{}' (category: {}, ticketId: {})",
                currentUserEmail, request.getCategory(), request.getTicketId());

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + currentUserEmail));

        RoleName role = currentUser.getRole().getName();

        // 1. RBAC Knowledge Visibility Rules
        List<String> allowedStatuses = null;
        List<String> allowedArticleIds = null;

        if (role == RoleName.ROLE_EMPLOYEE) {
            // Employees strictly only ever receive information from PUBLISHED knowledge articles
            allowedStatuses = List.of(ArticleStatus.PUBLISHED.name());
        } else if (role == RoleName.ROLE_ENGINEER) {
            // Engineers can access published articles plus their own authored drafts
            allowedStatuses = List.of(ArticleStatus.PUBLISHED.name());
            List<KnowledgeArticle> authoredArticles = articleRepository.findByAuthorId(currentUser.getId());
            if (!authoredArticles.isEmpty()) {
                allowedArticleIds = authoredArticles.stream()
                        .map(KnowledgeArticle::getId)
                        .collect(Collectors.toList());
            }
        }
        // Managers and Admins have full access (allowedStatuses remains null)

        // 2. IDOR-Protected Ticket Context
        Map<String, Object> ticketContextMap = null;
        TicketCategory effectiveCategory = request.getCategory();
        String ticketTitle = null;

        if (request.getTicketId() != null) {
            // getTicketById internally executes assertCanViewTicket(ticket, user)
            // Throws TicketAccessDeniedException if unauthorized (IDOR defense)
            TicketResponse ticket = ticketService.getTicketById(request.getTicketId(), currentUserEmail);
            ticketTitle = ticket.getTitle();

            ticketContextMap = new HashMap<>();
            ticketContextMap.put("id", ticket.getId());
            ticketContextMap.put("title", ticket.getTitle());
            ticketContextMap.put("description", ticket.getDescription());
            ticketContextMap.put("category", ticket.getCategory() != null ? ticket.getCategory().name() : null);
            ticketContextMap.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : null);
            ticketContextMap.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : null);

            // Default category from ticket if not specified in request
            if (effectiveCategory == null && ticket.getCategory() != null) {
                effectiveCategory = ticket.getCategory();
            }
        }

        // 3. Build RAG Payload for Python AI Service
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", request.getQuery().trim());
        if (effectiveCategory != null) {
            payload.put("category", effectiveCategory.name());
        }
        payload.put("topK", request.getTopK() != null ? request.getTopK() : 5);
        payload.put("minSimilarity", request.getMinSimilarity() != null ? request.getMinSimilarity() : 0.30);

        if (ticketContextMap != null) {
            payload.put("ticketContext", ticketContextMap);
        }
        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            payload.put("allowedStatuses", allowedStatuses);
        }
        if (allowedArticleIds != null && !allowedArticleIds.isEmpty()) {
            payload.put("allowedArticleIds", allowedArticleIds);
        }

        // 4. Dispatch RAG Generation
        CopilotAnswerResponse response = copilotClient.generateAnswer(payload);

        // 5. Enrich response with ticket metadata for client UI
        if (request.getTicketId() != null) {
            response.setTicketId(request.getTicketId());
            response.setTicketTitle(ticketTitle);
        }

        log.info("Copilot inquiry completed: grounded={}, confidence={}, chunksUsed={}",
                response.isGrounded(), response.getConfidence(), response.getRetrievedChunks());
        return response;
    }
}
