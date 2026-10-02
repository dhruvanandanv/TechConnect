package com.techconnect.dto.knowledge;

import com.techconnect.entity.enums.TicketCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticSearchRequest {

    @NotBlank(message = "Search query must not be blank")
    @Size(max = 1000, message = "Search query cannot exceed 1000 characters")
    private String query;

    private TicketCategory category;

    @Builder.Default
    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 20, message = "topK cannot exceed 20")
    private Integer topK = 5;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "minSimilarity must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minSimilarity cannot exceed 1.0")
    private Double minSimilarity = 0.50;
}
