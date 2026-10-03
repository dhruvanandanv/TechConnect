package com.techconnect.dto.copilot;

import com.techconnect.entity.enums.TicketCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming request payload for POST /api/ai/copilot/answer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotAnswerRequest {

    @NotBlank(message = "Support query cannot be blank")
    @Size(max = 1000, message = "Support query cannot exceed 1000 characters")
    private String query;

    private TicketCategory category;

    private Long ticketId;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 10, message = "topK cannot exceed 10")
    @Builder.Default
    private Integer topK = 5;

    @Min(value = 0, message = "minSimilarity must be between 0.0 and 1.0")
    @Max(value = 1, message = "minSimilarity must be between 0.0 and 1.0")
    @Builder.Default
    private Double minSimilarity = 0.30;
}
