package com.techconnect.dto.resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionSuggestionResponse {

    private Long ticketId;
    private String suggestion;
    private boolean grounded;

    @Builder.Default
    private List<String> steps = new ArrayList<>();

    @Builder.Default
    private List<ResolutionSourceDto> sources = new ArrayList<>();

    @Builder.Default
    private List<SimilarTicketDto> similarTickets = new ArrayList<>();

    private ResolutionRetrievalMetaDto retrievalMeta;
    private String provider;
    private String model;
    private Integer processingTimeMs;
}
