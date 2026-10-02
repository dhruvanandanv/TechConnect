package com.techconnect.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleReindexResponse {

    private String articleId;
    private int version;
    private String status;
    private int chunksCreated;
    private int chunksEmbedded;
    private String message;
}
