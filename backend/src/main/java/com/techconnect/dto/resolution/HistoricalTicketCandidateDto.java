package com.techconnect.dto.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalTicketCandidateDto {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String resolutionDescription;
}
