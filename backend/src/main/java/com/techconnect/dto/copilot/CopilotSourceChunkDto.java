package com.techconnect.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Provenance metadata for a retrieved knowledge chunk cited by the RAG Copilot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotSourceChunkDto {
    private String articleId;
    private String chunkId;
    private Integer articleVersion;
    private String title;
    private String section;
    private String content;
    private Double similarity;
    private String category;
    private List<String> tags;
}
