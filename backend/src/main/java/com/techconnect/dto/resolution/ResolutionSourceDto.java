package com.techconnect.dto.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionSourceDto {

    @Builder.Default
    private String type = "KNOWLEDGE_ARTICLE";

    private String articleId;
    private String chunkId;
    private String title;
    private String section;
    private Double similarity;
    private Integer version;
}
