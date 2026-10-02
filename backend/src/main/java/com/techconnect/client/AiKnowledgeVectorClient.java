package com.techconnect.client;

import com.techconnect.document.KnowledgeArticle;
import com.techconnect.dto.knowledge.ArticleReindexResponse;
import com.techconnect.dto.knowledge.IngestionRunResponse;
import com.techconnect.dto.knowledge.SemanticSearchResponse;
import com.techconnect.dto.knowledge.SemanticSearchResultChunk;
import com.techconnect.entity.enums.TicketCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

@Component
@Slf4j
public class AiKnowledgeVectorClient {

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiKnowledgeVectorClient(
            @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${ai.service.timeout-ms:5000}") int timeoutMs) {
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
     * Executes semantic vector search against Python AI service.
     */
    public SemanticSearchResponse semanticSearch(
            String query,
            TicketCategory category,
            Integer topK,
            Double minSimilarity,
            List<String> allowedStatuses,
            List<String> allowedArticleIds) {

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        if (category != null) {
            body.put("category", category.name());
        }
        body.put("topK", topK != null ? topK : 5);
        body.put("minSimilarity", minSimilarity != null ? minSimilarity : 0.50);
        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            body.put("allowedStatuses", allowedStatuses);
        }
        if (allowedArticleIds != null) {
            body.put("allowedArticleIds", allowedArticleIds);
        }

        try {
            log.debug("Dispatching semantic search to AI service at {}/api/v1/knowledge/semantic-search", aiServiceUrl);

            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/knowledge/semantic-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("results")) {
                List<Map<String, Object>> rawResults = (List<Map<String, Object>>) response.get("results");
                List<SemanticSearchResultChunk> chunks = new ArrayList<>();

                for (Map<String, Object> r : rawResults) {
                    chunks.add(SemanticSearchResultChunk.builder()
                            .articleId((String) r.get("articleId"))
                            .chunkId((String) r.get("chunkId"))
                            .articleVersion(r.get("articleVersion") != null ? ((Number) r.get("articleVersion")).intValue() : 1)
                            .title((String) r.get("title"))
                            .section((String) r.get("section"))
                            .content((String) r.get("content"))
                            .similarity(r.get("similarity") != null ? ((Number) r.get("similarity")).doubleValue() : 0.0)
                            .category((String) r.get("category"))
                            .tags(r.get("tags") instanceof List ? (List<String>) r.get("tags") : new ArrayList<>())
                            .build());
                }

                return SemanticSearchResponse.builder()
                        .searchType("SEMANTIC")
                        .query(query)
                        .available(true)
                        .totalHits(chunks.size())
                        .results(chunks)
                        .build();
            }

            return SemanticSearchResponse.unavailable(query, "Semantic search service returned empty response");

        } catch (ResourceAccessException ex) {
            log.warn("AI service unreachable or timed out during semantic search: {}", ex.getMessage());
            return SemanticSearchResponse.unavailable(query, "Semantic search is temporarily unavailable.");
        } catch (RestClientResponseException ex) {
            log.warn("AI service returned error during semantic search (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return SemanticSearchResponse.unavailable(query, "Semantic search is temporarily unavailable.");
        } catch (Exception ex) {
            log.error("Unexpected error in semantic search: {}", ex.getMessage());
            return SemanticSearchResponse.unavailable(query, "Semantic search encountered an unexpected issue.");
        }
    }

    /**
     * Dispatches a batch of articles to be chunked, embedded, and stored in vector DB.
     */
    public IngestionRunResponse ingestArticlesBatch(List<KnowledgeArticle> articles) {
        if (articles.isEmpty()) {
            return IngestionRunResponse.builder()
                    .articlesDiscovered(0)
                    .articlesProcessed(0)
                    .chunksCreated(0)
                    .chunksEmbedded(0)
                    .failures(0)
                    .message("No pending articles discovered for ingestion")
                    .build();
        }

        List<Map<String, Object>> payloadList = new ArrayList<>();
        for (KnowledgeArticle a : articles) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("articleId", a.getId());
            m.put("title", a.getTitle());
            m.put("summary", a.getSummary());
            m.put("problem", a.getProblem());
            m.put("cause", a.getCause());
            m.put("resolution", a.getResolution());
            m.put("content", a.getContent());
            m.put("category", a.getCategory() != null ? a.getCategory().name() : "GENERAL");
            m.put("tags", a.getTags() != null ? a.getTags() : Collections.emptyList());
            m.put("status", a.getStatus() != null ? a.getStatus().name() : "PUBLISHED");
            m.put("version", a.getVersion() != null ? a.getVersion() : 1);
            payloadList.add(m);
        }

        Map<String, Object> requestBody = Map.of("articles", payloadList);

        try {
            log.info("Sending batch of {} articles to AI ingestion at {}/api/v1/knowledge/ingest", articles.size(), aiServiceUrl);

            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/knowledge/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null) {
                int discovered = ((Number) response.getOrDefault("articlesDiscovered", articles.size())).intValue();
                int processed = ((Number) response.getOrDefault("articlesProcessed", 0)).intValue();
                int created = ((Number) response.getOrDefault("chunksCreated", 0)).intValue();
                int embedded = ((Number) response.getOrDefault("chunksEmbedded", 0)).intValue();
                int failures = ((Number) response.getOrDefault("failures", 0)).intValue();

                return IngestionRunResponse.builder()
                        .articlesDiscovered(discovered)
                        .articlesProcessed(processed)
                        .chunksCreated(created)
                        .chunksEmbedded(embedded)
                        .failures(failures)
                        .message("Ingestion batch completed successfully")
                        .build();
            }

            throw new IllegalStateException("Empty response from AI ingestion service");

        } catch (Exception ex) {
            log.error("AI service error during batch ingestion: {}", ex.getMessage());
            throw new RuntimeException("Knowledge ingestion service failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reindexes a single article version.
     */
    public ArticleReindexResponse reindexArticle(KnowledgeArticle article) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", article.getId());
        payload.put("articleId", article.getId());
        payload.put("title", article.getTitle());
        payload.put("summary", article.getSummary());
        payload.put("problem", article.getProblem());
        payload.put("cause", article.getCause());
        payload.put("resolution", article.getResolution());
        payload.put("content", article.getContent());
        payload.put("category", article.getCategory() != null ? article.getCategory().name() : "GENERAL");
        payload.put("tags", article.getTags() != null ? article.getTags() : Collections.emptyList());
        payload.put("status", article.getStatus() != null ? article.getStatus().name() : "PUBLISHED");
        payload.put("version", article.getVersion() != null ? article.getVersion() : 1);

        try {
            log.info("Reindexing article #{} at AI service", article.getId());
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/knowledge/reindex")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                int created = ((Number) response.getOrDefault("chunksCreated", 0)).intValue();
                int embedded = ((Number) response.getOrDefault("chunksEmbedded", 0)).intValue();
                return ArticleReindexResponse.builder()
                        .articleId(article.getId())
                        .version(article.getVersion())
                        .status("COMPLETED")
                        .chunksCreated(created)
                        .chunksEmbedded(embedded)
                        .message("Article reindexed successfully")
                        .build();
            }

            String err = response != null ? (String) response.get("error") : "Unknown reindex failure";
            throw new RuntimeException(err);

        } catch (Exception ex) {
            log.error("Failed to reindex article #{}: {}", article.getId(), ex.getMessage());
            throw new RuntimeException("Article reindexing failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Updates article status in vector store (e.g. ARCHIVED or DRAFT).
     */
    public void updateArticleStatus(String articleId, String status) {
        try {
            restClient.patch()
                    .uri("/api/v1/knowledge/articles/{id}/status", articleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Could not sync article #{} status {} to vector store: {}", articleId, status, ex.getMessage());
        }
    }

    /**
     * Deletes article chunks from vector store.
     */
    public void deleteArticleChunks(String articleId) {
        try {
            restClient.delete()
                    .uri("/api/v1/knowledge/articles/{id}", articleId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Could not delete vector chunks for article #{}: {}", articleId, ex.getMessage());
        }
    }
}
