package com.techconnect.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticSearchResponse {

    @Builder.Default
    private String searchType = "SEMANTIC";

    private String query;

    @Builder.Default
    private boolean available = true;

    private String message;

    private int totalHits;

    @Builder.Default
    private List<SemanticSearchResultChunk> results = new ArrayList<>();

    public static SemanticSearchResponse unavailable(String query, String message) {
        return SemanticSearchResponse.builder()
                .searchType("SEMANTIC")
                .query(query)
                .available(false)
                .message(message != null ? message : "Semantic search is temporarily unavailable.")
                .totalHits(0)
                .results(new ArrayList<>())
                .build();
    }
}
