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
}
