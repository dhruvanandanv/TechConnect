package com.techconnect.dto.knowledge;

import com.techconnect.entity.enums.KnowledgeArticleHistoryAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleHistoryResponse {

    private String id;
    private String articleId;
    private KnowledgeArticleHistoryAction action;
    private Long performedById;
    private String performedByName;
    private String performedByEmail;
    private LocalDateTime performedAt;
    private Integer version;
    private String details;
}
