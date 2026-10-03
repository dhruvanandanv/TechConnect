package com.techconnect.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured response returned by POST /api/ai/copilot/answer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotAnswerResponse {

    private String answer;
    private boolean grounded;
    private Double confidence;

    @Builder.Default
    private List<CopilotSourceChunkDto> sources = new ArrayList<>();

    private CopilotRetrievalMetaDto retrieval;
    private int retrievedChunks;
    private String model;
    private String provider;
    private long processingTimeMs;

    // Optional advisory ticket attribution
    private Long ticketId;
    private String ticketTitle;

    public static CopilotAnswerResponse unavailable(String message) {
        return CopilotAnswerResponse.builder()
                .answer(message)
                .grounded(false)
                .confidence(0.0)
                .sources(new ArrayList<>())
                .retrieval(null)
                .retrievedChunks(0)
                .model("unavailable")
                .provider("system-fallback")
                .processingTimeMs(0)
                .build();
    }
}
