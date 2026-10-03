package com.techconnect.service.impl;

import com.techconnect.client.AiSupportCopilotClient;
import com.techconnect.document.KnowledgeArticle;
import com.techconnect.dto.TicketResponse;
import com.techconnect.dto.resolution.HistoricalTicketCandidateDto;
import com.techconnect.dto.resolution.ResolutionSuggestionRequest;
import com.techconnect.dto.resolution.ResolutionSuggestionResponse;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.exception.ResourceNotFoundException;
import com.techconnect.exception.TicketAccessDeniedException;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
import com.techconnect.service.AiResolutionAssistantService;
import com.techconnect.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Production implementation of the AI Engineer Resolution Assistant Service.
 * Enforces strict engineer/manager/admin authorization, IDOR protection,
 * retrieves candidate resolved tickets from PostgreSQL, and orchestrates RAG generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiResolutionAssistantServiceImpl implements AiResolutionAssistantService {

    private final AiSupportCopilotClient copilotClient;
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final KnowledgeArticleRepository articleRepository;

    @Override
    @Transactional(readOnly = true)
    public ResolutionSuggestionResponse generateResolutionSuggestion(
            Long ticketId,
            ResolutionSuggestionRequest request,
            String currentUserEmail) {

        log.info("Processing resolution suggestion for ticket #{} requested by '{}'", ticketId, currentUserEmail);

        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + currentUserEmail));

        RoleName role = currentUser.getRole().getName();

        // 1. Role Authorization Check: Only Engineers, Managers, and Admins can access
        if (role == RoleName.ROLE_EMPLOYEE) {
            log.warn("Access denied: Employee '{}' attempted to use Engineer Resolution Assistant on ticket #{}",
                    currentUserEmail, ticketId);
            throw new TicketAccessDeniedException("Employees are not authorized to use the AI Engineer Resolution Assistant");
        }

        // 2. IDOR Protection: Validate ticket existence and current user's permission to view it
        // getTicketById internally invokes assertCanViewTicket(ticket, user)
        TicketResponse ticket = ticketService.getTicketById(ticketId, currentUserEmail);

        // 3. RBAC Knowledge Visibility Rules
        List<String> allowedStatuses = null;
        List<String> allowedArticleIds = null;

        if (role == RoleName.ROLE_ENGINEER) {
            // Engineers access published knowledge plus their own authored drafts
            allowedStatuses = List.of(ArticleStatus.PUBLISHED.name());
            List<KnowledgeArticle> authoredArticles = articleRepository.findByAuthorId(currentUser.getId());
            if (!authoredArticles.isEmpty()) {
                allowedArticleIds = authoredArticles.stream()
                        .map(KnowledgeArticle::getId)
                        .collect(Collectors.toList());
            }
        }
        // Managers and Admins have unrestricted knowledge base visibility (allowedStatuses = null)

        // 4. Retrieve Safe Candidate Historical Resolved Tickets from PostgreSQL
        List<Ticket> historicalResolvedTickets = ticketRepository.findResolvedTicketsForResolutionMatching(
                ticketId,
                PageRequest.of(0, 20)
        );

        List<HistoricalTicketCandidateDto> candidateDtos = new ArrayList<>();
        for (Ticket ht : historicalResolvedTickets) {
            String sanitizedDesc = ht.getDescription();
            if (sanitizedDesc != null && sanitizedDesc.length() > 500) {
                sanitizedDesc = sanitizedDesc.substring(0, 500) + "...";
            }

            String sanitizedRes = ht.getResolutionDescription();
            if (sanitizedRes != null && sanitizedRes.length() > 500) {
                sanitizedRes = sanitizedRes.substring(0, 500) + "...";
            }

            candidateDtos.add(HistoricalTicketCandidateDto.builder()
                    .id(ht.getId())
                    .title(ht.getTitle())
                    .description(sanitizedDesc)
                    .category(ht.getCategory() != null ? ht.getCategory().name() : null)
                    .priority(ht.getPriority() != null ? ht.getPriority().name() : null)
                    .resolutionDescription(sanitizedRes)
                    .build());
        }

        // 5. Build RAG Request Payload for Python AI Service
        Map<String, Object> currentTicketMap = new HashMap<>();
        currentTicketMap.put("id", ticket.getId());
        currentTicketMap.put("title", ticket.getTitle());
        currentTicketMap.put("description", ticket.getDescription());
        currentTicketMap.put("category", ticket.getCategory() != null ? ticket.getCategory().name() : null);
        currentTicketMap.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : null);
        currentTicketMap.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : null);

        int topK = (request != null && request.getTopK() != null) ? request.getTopK() : 5;
        double minSimilarity = (request != null && request.getMinSimilarity() != null) ? request.getMinSimilarity() : 0.30;

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("ticket", currentTicketMap);
        payload.put("candidateTickets", candidateDtos);
        payload.put("topK", topK);
        payload.put("minSimilarity", minSimilarity);

        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            payload.put("allowedStatuses", allowedStatuses);
        }
        if (allowedArticleIds != null && !allowedArticleIds.isEmpty()) {
            payload.put("allowedArticleIds", allowedArticleIds);
        }

        // 6. Call Python AI Service
        ResolutionSuggestionResponse response = copilotClient.generateResolutionSuggestion(payload);

        log.info("Resolution suggestion generated for ticket #{}: grounded={}, stepsCount={}, sourcesCount={}, similarTicketsCount={}",
                ticketId, response.isGrounded(), response.getSteps().size(), response.getSources().size(), response.getSimilarTickets().size());

        return response;
    }
}
