package com.techconnect.service;

import com.techconnect.dto.AiAnalysisRequest;
import com.techconnect.dto.AiAnalysisResponse;

public interface AiTicketIntelligenceService {

    /**
     * Request automated NLP analysis, category/priority prediction, and team routing
     * from the Python AI microservice with safe fallback when unavailable.
     *
     * @param request the ticket analysis request containing title and description
     * @return AI suggestions or graceful unavailable response
     */
    AiAnalysisResponse analyzeTicket(AiAnalysisRequest request);
}
