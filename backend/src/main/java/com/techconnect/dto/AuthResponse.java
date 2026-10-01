package com.techconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    @Builder.Default
    private boolean success = true;

    private String message;

    private String token;

    private String tokenType;

    private Long expiresIn;

    private UserResponse user;
}
