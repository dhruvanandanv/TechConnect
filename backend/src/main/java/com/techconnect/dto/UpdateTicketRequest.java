package com.techconnect.dto;

import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.TicketCategory;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTicketRequest {

    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @Size(min = 5, message = "Description must be at least 5 characters")
    private String description;

    private TicketCategory category;

    private Priority priority;

    private String resolutionDescription;
}
