package com.techconnect.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleSearchResponse {

    private String query;
    private long totalHits;
    private int page;
    private int size;
    private int totalPages;
    private List<KnowledgeArticleSummaryResponse> articles;
    private String searchType; // "KEYWORD" (clearly distinguished from future "SEMANTIC")
}
