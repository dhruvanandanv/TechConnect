package com.techconnect.dto.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionRetrievalMetaDto {

    private Integer topK;
    private Integer knowledgeChunksUsed;
    private Integer similarTicketsUsed;
    private Double bestSimilarity;
}
