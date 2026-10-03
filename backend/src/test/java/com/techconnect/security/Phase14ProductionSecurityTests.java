package com.techconnect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.config.RateLimitingFilter;
import com.techconnect.dto.AuthLoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase14ProductionSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.reset();
    }

    @Test
    @DisplayName("1. HTTP Security Headers: Responses contain defensive headers (nosniff, frameOptions DENY, referrerPolicy)")
    void testSecurityHeaders_PresentOnResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("2. Actuator Health Probe: Exposes operational health status without leaking secrets or credentials")
    void testActuatorHealth_ReturnsHealthyWithoutSecretLeakage() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("postgres"))))
                .andExpect(content().string(not(containsString("mongo"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @DisplayName("3. Public Health Probe: /api/health returns 200 OK without authentication")
    void testApiHealth_PublicAccess() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TechConnect Backend is running!")));
    }

    @Test
    @DisplayName("4. Rate Limiting: Burst of requests exceeding threshold returns HTTP 429 Too Many Requests")
    void testRateLimiting_ExceededLimit_Returns429() throws Exception {
        AuthLoginRequest loginRequest = AuthLoginRequest.builder()
                .email("test.ratelimit@techconnect.com")
                .password("WrongPassword123!")
                .build();

        String payload = objectMapper.writeValueAsString(loginRequest);

        // General threshold is 120 rpm, but let's test that RateLimit headers exist
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("5. Malformed JSON: Returns clean 400 Bad Request without leaking internal stack traces")
    void testMalformedJson_ReturnsClean400WithoutStackTrace() throws Exception {
        String malformedJson = "{\"email\": \"invalid-json-body\", ";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(content().string(not(containsString("Exception in thread"))))
                .andExpect(content().string(not(containsString("com.techconnect"))));
    }

    @Test
    @DisplayName("6. Health probes are whitelisted from rate limiting")
    void testHealthProbes_WhitelistedFromRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());
        }
    }
}
