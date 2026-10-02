package com.techconnect.repository.mongodb;

import com.techconnect.document.KnowledgeArticle;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeArticleRepository extends MongoRepository<KnowledgeArticle, String> {

    Optional<KnowledgeArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);

    Page<KnowledgeArticle> findByStatus(ArticleStatus status, Pageable pageable);

    Page<KnowledgeArticle> findByCategoryAndStatus(TicketCategory category, ArticleStatus status, Pageable pageable);

    Page<KnowledgeArticle> findByAuthorId(Long authorId, Pageable pageable);

    List<KnowledgeArticle> findByAuthorId(Long authorId);

    Page<KnowledgeArticle> findByAuthorIdAndStatus(Long authorId, ArticleStatus status, Pageable pageable);

    List<KnowledgeArticle> findBySourceTicketId(Long sourceTicketId);

    List<KnowledgeArticle> findByEmbeddingStatus(String embeddingStatus);
}
