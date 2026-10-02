package com.techconnect.dto.knowledge;

import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateKnowledgeArticleRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @NotBlank(message = "Summary is required")
    @Size(min = 5, max = 500, message = "Summary must be between 5 and 500 characters")
    private String summary;

    @NotNull(message = "Category is required")
    private TicketCategory category;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @NotBlank(message = "Problem/symptoms description is required")
    @Size(max = 10000, message = "Problem description must be less than 10,000 characters")
    private String problem;

    @Size(max = 10000, message = "Possible cause description must be less than 10,000 characters")
    private String cause;

    @NotBlank(message = "Resolution/steps is required")
    @Size(max = 20000, message = "Resolution steps must be less than 20,000 characters")
    private String resolution;

    @Size(max = 50000, message = "Content must be less than 50,000 characters")
    private String content;

    private ArticleStatus status; // Optional initial status (DRAFT or PUBLISHED). Defaults to DRAFT.

    private Long sourceTicketId; // Optional link to resolved ticket
}
