package com.techconnect.service.impl;

import com.techconnect.client.AiTicketIntelligenceClient;
import com.techconnect.dto.AiAnalysisRequest;
import com.techconnect.dto.AiAnalysisResponse;
import com.techconnect.service.AiTicketIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTicketIntelligenceServiceImpl implements AiTicketIntelligenceService {

    private final AiTicketIntelligenceClient aiClient;

    @Override
    public AiAnalysisResponse analyzeTicket(AiAnalysisRequest request) {
        log.info("Processing AI ticket intelligence request for title: '{}'", request.getTitle());
        AiAnalysisResponse response = aiClient.analyzeTicket(request);
        if (response.isAiAvailable()) {
            log.info("AI analysis succeeded: Category={}, Priority={}, Team={}",
                    response.getCategory() != null ? response.getCategory().getValue() : "N/A",
                    response.getPriority() != null ? response.getPriority().getValue() : "N/A",
                    response.getSuggestedTeam() != null ? response.getSuggestedTeam().getValue() : "N/A");
        } else {
            log.warn("AI analysis returned unavailable state: {}", response.getMessage());
        }
        return response;
    }
}
