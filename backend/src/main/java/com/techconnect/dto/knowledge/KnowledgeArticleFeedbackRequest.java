package com.techconnect.dto.knowledge;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeArticleFeedbackRequest {

    @NotNull(message = "helpful indicator must be true or false")
    private Boolean helpful;
}
