package com.techconnect.dto.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarTicketDto {

    private Long ticketId;
    private String title;
    private Double similarity;
    private String resolutionSummary;
    private String category;
    private String priority;
}
