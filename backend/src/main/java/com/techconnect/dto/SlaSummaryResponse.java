package com.techconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaSummaryResponse {

    private long totalActiveTickets;
    private long onTrack;
    private long atRisk;
    private long breached;
    private long responseSlaMet;
    private long responseSlaBreached;
    private long resolutionSlaMet;
    private long resolutionSlaBreached;
}
