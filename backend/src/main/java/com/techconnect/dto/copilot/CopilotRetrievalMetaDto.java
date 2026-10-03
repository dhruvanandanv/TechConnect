package com.techconnect.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retrieval metrics and audit statistics for Copilot transparency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotRetrievalMetaDto {
    private Integer topK;
    private Integer resultsUsed;
    private Double bestSimilarity;
}
