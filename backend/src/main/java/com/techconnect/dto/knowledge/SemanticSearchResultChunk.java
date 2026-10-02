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
public class SemanticSearchResultChunk {

    private String articleId;
    private String chunkId;
    private Integer articleVersion;
    private String title;
    private String slug;
    private String section;
    private String content;
    private Double similarity;
    private String category;

    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
