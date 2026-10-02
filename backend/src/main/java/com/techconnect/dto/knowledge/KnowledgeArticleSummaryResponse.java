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
public class KnowledgeArticleSummaryResponse {

    private String id;
    private String title;
    private String slug;
    private String summary;
    private TicketCategory category;
    private List<String> tags;
    private ArticleStatus status;
    private Long authorId;
    private String authorName;
    private Integer version;
    private Long viewCount;
    private Long helpfulCount;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private String embeddingStatus;
}
