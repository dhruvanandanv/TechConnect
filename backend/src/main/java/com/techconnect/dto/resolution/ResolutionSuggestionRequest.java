package com.techconnect.dto.resolution;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionSuggestionRequest {

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 10, message = "topK cannot exceed 10")
    @Builder.Default
    private Integer topK = 5;

    @DecimalMin(value = "0.0", message = "minSimilarity cannot be negative")
    @DecimalMax(value = "1.0", message = "minSimilarity cannot exceed 1.0")
    @Builder.Default
    private Double minSimilarity = 0.30;
}
