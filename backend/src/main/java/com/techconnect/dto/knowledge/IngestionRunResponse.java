package com.techconnect.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionRunResponse {

    private int articlesDiscovered;
    private int articlesProcessed;
    private int chunksCreated;
    private int chunksEmbedded;
    private int failures;
    private String message;
}
