package com.techconnect.document;

import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MongoDB document representing a TechConnect Knowledge Base Article.
 * Optimized for flexible document structure, troubleshooting steps, and future RAG pipelines.
 */
@Document(collection = "knowledge_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticle {

    @Id
    private String id;

    @TextIndexed(weight = 5)
    @Indexed
    @Field("title")
    private String title;

    @Indexed(unique = true)
    @Field("slug")
    private String slug;

    @TextIndexed(weight = 3)
    @Field("summary")
    private String summary;

    @TextIndexed(weight = 2)
    @Field("problem")
    private String problem;

    @TextIndexed(weight = 2)
    @Field("cause")
    private String cause;

    @TextIndexed(weight = 4)
    @Field("resolution")
    private String resolution;

    @Field("content")
    private String content;

    @Indexed
    @Field("category")
    private TicketCategory category;

    @TextIndexed(weight = 3)
    @Indexed
    @Field("tags")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Indexed
    @Field("status")
    @Builder.Default
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Indexed
    @Field("author_id")
    private Long authorId;

    @Field("author_name")
    private String authorName;

    @Field("author_email")
    private String authorEmail;

    @Field("version")
    @Builder.Default
    private Integer version = 1;

    @Field("view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Field("helpful_count")
    @Builder.Default
    private Long helpfulCount = 0L;

    @Field("not_helpful_count")
    @Builder.Default
    private Long notHelpfulCount = 0L;

    @Field("feedback_user_ids")
    @Builder.Default
    private Set<Long> feedbackUserIds = new HashSet<>();

    @Field("source_type")
    @Builder.Default
    private String sourceType = "MANUAL"; // "MANUAL" or "TICKET"

    @Indexed
    @Field("source_ticket_id")
    private Long sourceTicketId;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Field("published_at")
    private LocalDateTime publishedAt;

    @Field("archived_at")
    private LocalDateTime archivedAt;

    // --- Semantic Search / Vector Embedding Fields (Phase 11) ---
    @Field("normalized_text")
    private String normalizedText;

    @Indexed
    @Field("embedding_status")
    @Builder.Default
    private String embeddingStatus = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Field("embedding_model")
    private String embeddingModel;

    @Field("embedding_version")
    private String embeddingVersion;

    @Field("embedding_updated_at")
    private LocalDateTime embeddingUpdatedAt;

    @Field("embedding_error")
    private String embeddingError;
}
