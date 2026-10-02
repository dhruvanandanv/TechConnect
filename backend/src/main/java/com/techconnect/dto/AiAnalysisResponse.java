package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAnalysisResponse {

    private boolean aiAvailable;

    private String message;

    private AiPredictionScore category;

    private AiPredictionScore priority;

    @JsonProperty("suggested_team")
    @JsonAlias({"suggestedTeam", "suggested_team"})
    private AiPredictionScore suggestedTeam;

    private String summary;

    private List<String> reasons;

    @JsonProperty("model_version")
    @JsonAlias({"modelVersion", "model_version"})
    private String modelVersion;

    @JsonProperty("processing_time_ms")
    @JsonAlias({"processingTimeMs", "processing_time_ms"})
    private Integer processingTimeMs;

    public static AiAnalysisResponse unavailable(String message) {
        return AiAnalysisResponse.builder()
                .aiAvailable(false)
                .message(message != null ? message : "AI analysis is currently unavailable")
                .build();
    }
}
