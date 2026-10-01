package com.techconnect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCommentRequest {

    @NotBlank(message = "Comment content cannot be blank")
    private String content;

    @Builder.Default
    private Boolean isInternal = false;
}
