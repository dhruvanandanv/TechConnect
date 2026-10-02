package com.techconnect.client;

import com.techconnect.dto.AiAnalysisRequest;
import com.techconnect.dto.AiAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class AiTicketIntelligenceClient {

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiTicketIntelligenceClient(
            @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${ai.service.timeout-ms:3000}") int timeoutMs) {
        this.aiServiceUrl = aiServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public AiAnalysisResponse analyzeTicket(AiAnalysisRequest request) {
        try {
            log.debug("Dispatching ticket analysis request to AI service at {}/api/v1/ticket-intelligence/analyze", aiServiceUrl);
            AiAnalysisResponse response = restClient.post()
                    .uri("/api/v1/ticket-intelligence/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiAnalysisResponse.class);

            if (response != null) {
                response.setAiAvailable(true);
                response.setMessage("AI analysis completed successfully");
                return response;
            }
            return AiAnalysisResponse.unavailable("AI service returned empty response");
        } catch (ResourceAccessException ex) {
            log.warn("AI service unreachable or timed out at {}: {}", aiServiceUrl, ex.getMessage());
            return AiAnalysisResponse.unavailable("AI analysis service is unreachable or timed out");
        } catch (RestClientResponseException ex) {
            log.warn("AI service returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return AiAnalysisResponse.unavailable("AI analysis service returned an error (" + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            log.error("Unexpected error communicating with AI service: {}", ex.getMessage());
            return AiAnalysisResponse.unavailable("An unexpected error occurred during AI analysis");
        }
    }
}
