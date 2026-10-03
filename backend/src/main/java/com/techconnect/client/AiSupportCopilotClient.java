package com.techconnect.client;

import com.techconnect.dto.copilot.CopilotAnswerResponse;
import com.techconnect.dto.copilot.CopilotRetrievalMetaDto;
import com.techconnect.dto.copilot.CopilotSourceChunkDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST Client connecting Spring Boot to the Python RAG AI Support Copilot Service.
 * Dispatches queries to POST /api/v1/rag/answer with strict timeout and graceful fallback.
 */
@Component
@Slf4j
public class AiSupportCopilotClient {

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiSupportCopilotClient(
            @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${ai.copilot.timeout-ms:10000}") int timeoutMs) {
        this.aiServiceUrl = aiServiceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Calls Python RAG service /api/v1/rag/answer with query, filters, and optional ticket context.
     */
    public CopilotAnswerResponse generateAnswer(Map<String, Object> payload) {
        try {
            log.debug("Dispatching RAG request to AI Copilot at {}/api/v1/rag/answer", aiServiceUrl);

            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/rag/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("answer")) {
                return mapToResponse(response);
            }

            return CopilotAnswerResponse.unavailable(
                    "The AI Support Copilot returned an empty response. You can still search the Knowledge Base directly."
            );

        } catch (ResourceAccessException ex) {
            log.warn("AI Copilot service unreachable or timed out at {}: {}", aiServiceUrl, ex.getMessage());
            return CopilotAnswerResponse.unavailable(
                    "The AI Support Copilot is temporarily unavailable. You can still use Knowledge Base search."
            );
        } catch (RestClientResponseException ex) {
            log.warn("AI Copilot returned error status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return CopilotAnswerResponse.unavailable(
                    "The AI Support Copilot is temporarily unavailable. You can still use Knowledge Base search."
            );
        } catch (Exception ex) {
            log.error("Unexpected error in AI Copilot client: {}", ex.getMessage());
            return CopilotAnswerResponse.unavailable(
                    "An unexpected error occurred while communicating with the AI Copilot."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private CopilotAnswerResponse mapToResponse(Map<String, Object> map) {
        String answer = (String) map.getOrDefault("answer", "");
        boolean grounded = Boolean.TRUE.equals(map.get("grounded"));
        Double confidence = map.get("confidence") != null ? ((Number) map.get("confidence")).doubleValue() : null;
        int retrievedChunks = map.get("retrievedChunks") != null ? ((Number) map.get("retrievedChunks")).intValue() : 0;
        String model = (String) map.getOrDefault("model", "unknown");
        String provider = (String) map.getOrDefault("provider", "unknown");
        long processingTimeMs = map.get("processingTimeMs") != null ? ((Number) map.get("processingTimeMs")).longValue() : 0L;

        List<CopilotSourceChunkDto> sources = new ArrayList<>();
        if (map.get("sources") instanceof List) {
            List<Map<String, Object>> rawSources = (List<Map<String, Object>>) map.get("sources");
            for (Map<String, Object> s : rawSources) {
                sources.add(CopilotSourceChunkDto.builder()
                        .articleId((String) s.get("articleId"))
                        .chunkId((String) s.get("chunkId"))
                        .articleVersion(s.get("articleVersion") != null ? ((Number) s.get("articleVersion")).intValue() : 1)
                        .title((String) s.get("title"))
                        .section((String) s.get("section"))
                        .content((String) s.get("content"))
                        .similarity(s.get("similarity") != null ? ((Number) s.get("similarity")).doubleValue() : 0.0)
                        .category((String) s.get("category"))
                        .tags(s.get("tags") instanceof List ? (List<String>) s.get("tags") : new ArrayList<>())
                        .build());
            }
        }

        CopilotRetrievalMetaDto retrieval = null;
        if (map.get("retrieval") instanceof Map) {
            Map<String, Object> rMap = (Map<String, Object>) map.get("retrieval");
            retrieval = CopilotRetrievalMetaDto.builder()
                    .topK(rMap.get("topK") != null ? ((Number) rMap.get("topK")).intValue() : 0)
                    .resultsUsed(rMap.get("resultsUsed") != null ? ((Number) rMap.get("resultsUsed")).intValue() : 0)
                    .bestSimilarity(rMap.get("bestSimilarity") != null ? ((Number) rMap.get("bestSimilarity")).doubleValue() : 0.0)
                    .build();
        }

        return CopilotAnswerResponse.builder()
                .answer(answer)
                .grounded(grounded)
                .confidence(confidence)
                .sources(sources)
                .retrieval(retrieval)
                .retrievedChunks(retrievedChunks)
                .model(model)
                .provider(provider)
                .processingTimeMs(processingTimeMs)
                .build();
    }

    /**
     * Calls Python RAG service /api/v1/rag/resolution-suggestion with ticket and candidate historical tickets.
     */
    public com.techconnect.dto.resolution.ResolutionSuggestionResponse generateResolutionSuggestion(Map<String, Object> payload) {
        Long ticketId = payload.get("ticketId") != null ? ((Number) payload.get("ticketId")).longValue() : null;

        try {
            log.debug("Dispatching resolution suggestion request to AI service at {}/api/v1/rag/resolution-suggestion", aiServiceUrl);

            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/rag/resolution-suggestion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("suggestion")) {
                return mapToResolutionResponse(response, ticketId);
            }

            return buildFallbackResolutionResponse(
                    ticketId,
                    "The AI Resolution Assistant returned an empty proposal. Please review documentation manually."
            );

        } catch (ResourceAccessException ex) {
            log.warn("AI service unreachable or timed out for resolution suggestion at {}: {}", aiServiceUrl, ex.getMessage());
            return buildFallbackResolutionResponse(
                    ticketId,
                    "The AI Resolution Assistant is temporarily unavailable. You can still consult Knowledge Base articles and related tickets manually."
            );
        } catch (RestClientResponseException ex) {
            log.warn("AI service returned error status {} for resolution suggestion: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return buildFallbackResolutionResponse(
                    ticketId,
                    "The AI Resolution Assistant is temporarily unavailable. You can still consult Knowledge Base articles and related tickets manually."
            );
        } catch (Exception ex) {
            log.error("Unexpected error in AI resolution suggestion client: {}", ex.getMessage());
            return buildFallbackResolutionResponse(
                    ticketId,
                    "An unexpected error occurred while communicating with the AI Resolution Assistant."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private com.techconnect.dto.resolution.ResolutionSuggestionResponse mapToResolutionResponse(
            Map<String, Object> map, Long fallbackTicketId) {

        Long ticketId = map.get("ticketId") != null
                ? ((Number) map.get("ticketId")).longValue()
                : fallbackTicketId;
        String suggestion = (String) map.getOrDefault("suggestion", "");
        boolean grounded = Boolean.TRUE.equals(map.get("grounded"));
        String model = (String) map.getOrDefault("model", "unknown");
        String provider = (String) map.getOrDefault("provider", "unknown");
        int processingTimeMs = map.get("processingTimeMs") != null
                ? ((Number) map.get("processingTimeMs")).intValue()
                : 0;

        List<String> steps = new ArrayList<>();
        if (map.get("steps") instanceof List) {
            steps = (List<String>) map.get("steps");
        }

        List<com.techconnect.dto.resolution.ResolutionSourceDto> sources = new ArrayList<>();
        if (map.get("sources") instanceof List) {
            List<Map<String, Object>> rawSources = (List<Map<String, Object>>) map.get("sources");
            for (Map<String, Object> s : rawSources) {
                sources.add(com.techconnect.dto.resolution.ResolutionSourceDto.builder()
                        .type((String) s.getOrDefault("type", "KNOWLEDGE_ARTICLE"))
                        .articleId((String) s.get("articleId"))
                        .chunkId((String) s.get("chunkId"))
                        .title((String) s.get("title"))
                        .section((String) s.get("section"))
                        .similarity(s.get("similarity") != null ? ((Number) s.get("similarity")).doubleValue() : 0.0)
                        .version(s.get("version") != null ? ((Number) s.get("version")).intValue() : 1)
                        .build());
            }
        }

        List<com.techconnect.dto.resolution.SimilarTicketDto> similarTickets = new ArrayList<>();
        if (map.get("similarTickets") instanceof List) {
            List<Map<String, Object>> rawTickets = (List<Map<String, Object>>) map.get("similarTickets");
            for (Map<String, Object> t : rawTickets) {
                similarTickets.add(com.techconnect.dto.resolution.SimilarTicketDto.builder()
                        .ticketId(t.get("ticketId") != null ? ((Number) t.get("ticketId")).longValue() : null)
                        .title((String) t.get("title"))
                        .similarity(t.get("similarity") != null ? ((Number) t.get("similarity")).doubleValue() : 0.0)
                        .resolutionSummary((String) t.get("resolutionSummary"))
                        .category((String) t.get("category"))
                        .priority((String) t.get("priority"))
                        .build());
            }
        }

        com.techconnect.dto.resolution.ResolutionRetrievalMetaDto retrieval = null;
        if (map.get("retrievalMeta") instanceof Map) {
            Map<String, Object> rMap = (Map<String, Object>) map.get("retrievalMeta");
            retrieval = com.techconnect.dto.resolution.ResolutionRetrievalMetaDto.builder()
                    .topK(rMap.get("topK") != null ? ((Number) rMap.get("topK")).intValue() : 0)
                    .knowledgeChunksUsed(rMap.get("knowledgeChunksUsed") != null ? ((Number) rMap.get("knowledgeChunksUsed")).intValue() : 0)
                    .similarTicketsUsed(rMap.get("similarTicketsUsed") != null ? ((Number) rMap.get("similarTicketsUsed")).intValue() : 0)
                    .bestSimilarity(rMap.get("bestSimilarity") != null ? ((Number) rMap.get("bestSimilarity")).doubleValue() : 0.0)
                    .build();
        }

        return com.techconnect.dto.resolution.ResolutionSuggestionResponse.builder()
                .ticketId(ticketId)
                .suggestion(suggestion)
                .grounded(grounded)
                .steps(steps)
                .sources(sources)
                .similarTickets(similarTickets)
                .retrievalMeta(retrieval)
                .provider(provider)
                .model(model)
                .processingTimeMs(processingTimeMs)
                .build();
    }

    private com.techconnect.dto.resolution.ResolutionSuggestionResponse buildFallbackResolutionResponse(
            Long ticketId, String message) {
        return com.techconnect.dto.resolution.ResolutionSuggestionResponse.builder()
                .ticketId(ticketId)
                .suggestion(message)
                .grounded(false)
                .steps(new ArrayList<>())
                .sources(new ArrayList<>())
                .similarTickets(new ArrayList<>())
                .provider("unavailable")
                .model("unavailable")
                .processingTimeMs(0)
                .build();
    }
}
