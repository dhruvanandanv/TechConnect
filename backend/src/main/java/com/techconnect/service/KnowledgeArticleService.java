package com.techconnect.service;

import com.techconnect.dto.knowledge.*;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface KnowledgeArticleService {

    KnowledgeArticleResponse createArticle(CreateKnowledgeArticleRequest request, String userEmail);

    KnowledgeArticleResponse updateArticle(String id, UpdateKnowledgeArticleRequest request, String userEmail);

    KnowledgeArticleResponse publishArticle(String id, String userEmail);

    KnowledgeArticleResponse archiveArticle(String id, String userEmail);

    KnowledgeArticleResponse revertToDraft(String id, String userEmail);

    KnowledgeArticleResponse getArticleById(String id, String userEmail);

    KnowledgeArticleResponse getArticleBySlug(String slug, String userEmail);

    Page<KnowledgeArticleSummaryResponse> getArticles(
            TicketCategory category,
            ArticleStatus status,
            String tag,
            Pageable pageable,
            String userEmail);

    KnowledgeArticleSearchResponse searchArticles(String query, Pageable pageable, String userEmail);

    KnowledgeArticleResponse submitFeedback(String id, KnowledgeArticleFeedbackRequest request, String userEmail);

    List<KnowledgeArticleHistoryResponse> getArticleHistory(String id, String userEmail);

    List<String> getCategories();

    List<String> getTags();
}
