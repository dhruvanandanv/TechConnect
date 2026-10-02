package com.techconnect.dto.knowledge;

import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleResponse {

    private String id;
    private String title;
    private String slug;
    private String summary;
    private String problem;
    private String cause;
    private String resolution;
    private String content;
    private TicketCategory category;
    private List<String> tags;
    private ArticleStatus status;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private Integer version;
    private Long viewCount;
    private Long helpfulCount;
    private Long notHelpfulCount;
    private Boolean userHasVoted;
    private String sourceType;
    private Long sourceTicketId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime archivedAt;
}
