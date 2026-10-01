package com.techconnect.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private Long departmentId;
    private String departmentName;
    private Long teamId;
    private String teamName;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
