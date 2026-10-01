package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketCommentResponse {

    private Long id;
    private Long ticketId;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String authorRole;
    private String content;
    private Boolean isInternal;
    private LocalDateTime createdAt;
}
