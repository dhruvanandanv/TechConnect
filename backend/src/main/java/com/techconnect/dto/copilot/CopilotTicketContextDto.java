package com.techconnect.dto.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Advisory active ticket metadata sent to the RAG copilot.
 * Used exclusively for contextual relevance; never modifies ticket status or ownership.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotTicketContextDto {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String status;
}
