package com.techconnect.document;

import com.techconnect.entity.enums.KnowledgeArticleHistoryAction;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * MongoDB document tracking versioned audit history for knowledge articles.
 */
@Document(collection = "knowledge_article_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleHistory {

    @Id
    private String id;

    @Indexed
    @Field("article_id")
    private String articleId;

    @Field("action")
    private KnowledgeArticleHistoryAction action;

    @Field("performed_by_id")
    private Long performedById;

    @Field("performed_by_name")
    private String performedByName;

    @Field("performed_by_email")
    private String performedByEmail;

    @Field("performed_at")
    private LocalDateTime performedAt;

    @Field("version")
    private Integer version;

    @Field("details")
    private String details;
}
