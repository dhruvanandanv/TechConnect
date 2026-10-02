package com.techconnect.dto.knowledge;

import com.techconnect.entity.enums.TicketCategory;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKnowledgeArticleRequest {

    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @Size(min = 5, max = 500, message = "Summary must be between 5 and 500 characters")
    private String summary;

    private TicketCategory category;

    private List<String> tags;

    @Size(max = 10000, message = "Problem description must be less than 10,000 characters")
    private String problem;

    @Size(max = 10000, message = "Possible cause description must be less than 10,000 characters")
    private String cause;

    @Size(max = 20000, message = "Resolution steps must be less than 20,000 characters")
    private String resolution;

    @Size(max = 50000, message = "Content must be less than 50,000 characters")
    private String content;

    private Long sourceTicketId;
}
