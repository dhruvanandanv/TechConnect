package com.techconnect.controller;

import com.techconnect.dto.knowledge.*;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.service.KnowledgeArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeArticleController {

    private final KnowledgeArticleService articleService;

    // =========================================================================
    // Public Authenticated Read Endpoints
    // =========================================================================

    @GetMapping("/articles")
    public ResponseEntity<Page<KnowledgeArticleSummaryResponse>> getArticles(
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) String tag,
            @PageableDefault(size = 10, sort = "updated_at", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<KnowledgeArticleSummaryResponse> response = articleService.getArticles(
                category, status, tag, pageable, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<KnowledgeArticleResponse> getArticleById(
            @PathVariable String id,
            Authentication authentication) {
        KnowledgeArticleResponse response = articleService.getArticleById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/articles/slug/{slug}")
    public ResponseEntity<KnowledgeArticleResponse> getArticleBySlug(
            @PathVariable String slug,
            Authentication authentication) {
        KnowledgeArticleResponse response = articleService.getArticleBySlug(slug, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<KnowledgeArticleSearchResponse> searchArticles(
            @RequestParam(name = "q", defaultValue = "") String query,
            @PageableDefault(size = 10, sort = "view_count", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        KnowledgeArticleSearchResponse response = articleService.searchArticles(query, pageable, authentication.getName());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Phase 11 Semantic Search (Dense Vector Retrieval)
    // =========================================================================

    @GetMapping("/semantic-search")
    public ResponseEntity<SemanticSearchResponse> semanticSearchGet(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false, defaultValue = "5") Integer topK,
            @RequestParam(required = false, defaultValue = "0.50") Double minSimilarity,
            Authentication authentication) {
        SemanticSearchRequest request = SemanticSearchRequest.builder()
                .query(query)
                .category(category)
                .topK(topK)
                .minSimilarity(minSimilarity)
                .build();
        SemanticSearchResponse response = articleService.semanticSearch(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/semantic-search")
    public ResponseEntity<SemanticSearchResponse> semanticSearchPost(
            @Valid @RequestBody SemanticSearchRequest request,
            Authentication authentication) {
        SemanticSearchResponse response = articleService.semanticSearch(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(articleService.getCategories());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getTags() {
        return ResponseEntity.ok(articleService.getTags());
    }

    // =========================================================================
    // Knowledge Vector Ingestion & Reindexing (Staff Only)
    // =========================================================================

    @PostMapping("/ingestion/run")
    public ResponseEntity<IngestionRunResponse> runIngestion(Authentication authentication) {
        log.info("REST request to trigger knowledge ingestion by user '{}'", authentication.getName());
        IngestionRunResponse response = articleService.runIngestion(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/articles/{id}/reindex")
    public ResponseEntity<ArticleReindexResponse> reindexArticle(
            @PathVariable String id,
            Authentication authentication) {
        log.info("REST request to reindex knowledge article #{} by user '{}'", id, authentication.getName());
        ArticleReindexResponse response = articleService.reindexArticle(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Staff Article Management Endpoints (ENGINEER, MANAGER, ADMIN)
    // =========================================================================

    @PostMapping("/articles")
    public ResponseEntity<KnowledgeArticleResponse> createArticle(
            @Valid @RequestBody CreateKnowledgeArticleRequest request,
            Authentication authentication) {
        log.info("REST request to create article '{}' by user '{}'", request.getTitle(), authentication.getName());
        KnowledgeArticleResponse response = articleService.createArticle(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/articles/{id}")
    public ResponseEntity<KnowledgeArticleResponse> updateArticle(
            @PathVariable String id,
            @Valid @RequestBody UpdateKnowledgeArticleRequest request,
            Authentication authentication) {
        log.info("REST request to update article #{} by user '{}'", id, authentication.getName());
        KnowledgeArticleResponse response = articleService.updateArticle(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/articles/{id}/publish")
    public ResponseEntity<KnowledgeArticleResponse> publishArticle(
            @PathVariable String id,
            Authentication authentication) {
        log.info("REST request to publish article #{} by user '{}'", id, authentication.getName());
        KnowledgeArticleResponse response = articleService.publishArticle(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/articles/{id}/archive")
    public ResponseEntity<KnowledgeArticleResponse> archiveArticle(
            @PathVariable String id,
            Authentication authentication) {
        log.info("REST request to archive article #{} by user '{}'", id, authentication.getName());
        KnowledgeArticleResponse response = articleService.archiveArticle(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/articles/{id}/draft")
    public ResponseEntity<KnowledgeArticleResponse> revertToDraft(
            @PathVariable String id,
            Authentication authentication) {
        log.info("REST request to revert article #{} to draft by user '{}'", id, authentication.getName());
        KnowledgeArticleResponse response = articleService.revertToDraft(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/articles/{id}/history")
    public ResponseEntity<List<KnowledgeArticleHistoryResponse>> getArticleHistory(
            @PathVariable String id,
            Authentication authentication) {
        List<KnowledgeArticleHistoryResponse> response = articleService.getArticleHistory(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // User Interaction & Feedback
    // =========================================================================

    @PostMapping("/articles/{id}/feedback")
    public ResponseEntity<KnowledgeArticleResponse> submitFeedback(
            @PathVariable String id,
            @Valid @RequestBody KnowledgeArticleFeedbackRequest request,
            Authentication authentication) {
        KnowledgeArticleResponse response = articleService.submitFeedback(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
