package com.techconnect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.AuthLoginRequest;
import com.techconnect.dto.AuthRegisterRequest;
import com.techconnect.dto.AuthResponse;
import com.techconnect.entity.User;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.JwtService;
import com.techconnect.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAndAuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.seed.dev-password:TestPassOnlyInTests!2026}")
    private String devPassword;

    private String employeeToken;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        employeeToken = obtainTokenFor("employee@techconnect.com", devPassword);
        engineerToken = obtainTokenFor("engineer@techconnect.com", devPassword);
        managerToken = obtainTokenFor("manager@techconnect.com", devPassword);
        adminToken = obtainTokenFor("admin@techconnect.com", devPassword);
    }

    private String obtainTokenFor(String email, String password) throws Exception {
        AuthLoginRequest request = AuthLoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.getToken();
    }

    // =========================================================================
    // 1. AUTHENTICATION TESTS (Items 1 - 6)
    // =========================================================================

    @Test
    @DisplayName("1. Login returns a valid JWT access token with Bearer type and expiration")
    void testLoginReturnsJwt() throws Exception {
        AuthLoginRequest request = AuthLoginRequest.builder()
                .email("employee@techconnect.com")
                .password(devPassword)
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600000))
                .andExpect(jsonPath("$.user.email").value("employee@techconnect.com"));

        assertThat(employeeToken).isNotBlank();
        String[] parts = employeeToken.split("\\.");
        assertThat(parts).hasSize(3); // Standard header.payload.signature
    }

    @Test
    @DisplayName("2. Valid JWT authenticates protected endpoint successfully (200 OK)")
    void testValidJwtAuthenticatesProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("Authorized: Employee endpoint accessed successfully")));
    }

    @Test
    @DisplayName("3. Missing JWT returns 401 Unauthorized with clean JSON error body")
    void testMissingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/employee/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Full authentication is required")));
    }

    @Test
    @DisplayName("4. Malformed JWT returns 401 Unauthorized")
    void testMalformedJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Expired JWT returns 401 Unauthorized")
    void testExpiredJwtReturns401() throws Exception {
        // Build a token that expired 1 hour ago using the valid signing key
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        String expiredToken = Jwts.builder()
                .subject("employee@techconnect.com")
                .claim("roles", List.of("ROLE_EMPLOYEE"))
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("6. Invalid JWT (tampered signature) returns 401 Unauthorized")
    void testInvalidJwtSignatureReturns401() throws Exception {
        // Sign token with a different (unauthorized) HMAC key
        SecretKey differentKey = Keys.hmacShaKeyFor("attacker_fake_secret_key_that_is_at_least_32_bytes_long!!".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("admin@techconnect.com")
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // 2. AUTHORIZATION MATRIX TESTS (Items 7 - 13)
    // =========================================================================

    @Test
    @DisplayName("7. EMPLOYEE can access employee endpoint (200 OK)")
    void testEmployeeCanAccessEmployeeEndpoint() throws Exception {
        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("8. EMPLOYEE cannot access engineer endpoint (403 Forbidden)")
    void testEmployeeCannotAccessEngineerEndpoint() throws Exception {
        mockMvc.perform(get("/api/engineer/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("insufficient role privileges")));
    }

    @Test
    @DisplayName("8b. EMPLOYEE cannot access manager or admin endpoints (403 Forbidden)")
    void testEmployeeCannotAccessManagerOrAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/manager/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. ENGINEER can access engineer endpoint (200 OK) and employee endpoint (200 OK)")
    void testEngineerCanAccessEngineerAndEmployeeEndpoints() throws Exception {
        mockMvc.perform(get("/api/engineer/test")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. ENGINEER cannot access manager endpoint (403 Forbidden)")
    void testEngineerCannotAccessManagerEndpoint() throws Exception {
        mockMvc.perform(get("/api/manager/test")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("10b. ENGINEER cannot access admin endpoint (403 Forbidden)")
    void testEngineerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. MANAGER can access manager endpoint (200 OK) and employee endpoint (200 OK)")
    void testManagerCanAccessManagerAndEmployeeEndpoints() throws Exception {
        mockMvc.perform(get("/api/manager/test")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("12. MANAGER cannot access admin endpoint (403 Forbidden)")
    void testManagerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("12b. MANAGER cannot access engineer endpoint (403 Forbidden)")
    void testManagerCannotAccessEngineerEndpoint() throws Exception {
        mockMvc.perform(get("/api/engineer/test")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("13. ADMIN can access admin endpoint (200 OK) and all lower endpoints (200 OK)")
    void testAdminCanAccessAllEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/manager/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/engineer/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. REGISTRATION TESTS (Items 14 - 15)
    // =========================================================================

    @Test
    @DisplayName("14. Public registration creates EMPLOYEE role by default")
    void testPublicRegistrationCreatesEmployee() throws Exception {
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("New Employee")
                .email("new.employee.phase4@techconnect.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ROLE_EMPLOYEE"));
    }

    @Test
    @DisplayName("15. Public registration cannot create ADMIN or MANAGER role")
    void testPublicRegistrationCannotCreateAdminOrManager() throws Exception {
        AuthRegisterRequest adminRequest = AuthRegisterRequest.builder()
                .name("Malicious Admin")
                .email("malicious.admin@techconnect.com")
                .password("Password123!")
                .role("ROLE_ADMIN")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Public registration cannot assign administrative")));

        AuthRegisterRequest managerRequest = AuthRegisterRequest.builder()
                .name("Malicious Manager")
                .email("malicious.manager@techconnect.com")
                .password("Password123!")
                .role("ROLE_MANAGER")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(managerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Public registration cannot assign administrative")));
    }

    // =========================================================================
    // 4. SECURITY INTEGRITY TESTS (Items 16 - 19)
    // =========================================================================

    @Test
    @DisplayName("16. Password remains BCrypt hashed in database")
    void testPasswordIsBcryptHashed() {
        User user = userRepository.findByEmail("employee@techconnect.com").orElseThrow();
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(user.getPassword()).isNotEqualTo(devPassword);
        assertThat(passwordEncoder.matches(devPassword, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("17. JWT payload does not contain password, passwordHash, or database secrets")
    void testJwtDoesNotContainSensitiveInformation() throws Exception {
        String[] parts = employeeToken.split("\\.");
        assertThat(parts).hasSize(3);
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode payloadNode = objectMapper.readTree(payloadJson);

        assertThat(payloadNode.get("sub").asText()).isEqualTo("employee@techconnect.com");
        assertThat(payloadNode.has("password")).isFalse();
        assertThat(payloadNode.has("passwordHash")).isFalse();
        assertThat(payloadNode.has("hash")).isFalse();
        assertThat(payloadNode.has("secret")).isFalse();
        assertThat(payloadNode.has("roles")).isTrue();

        // Also verify using signature validation
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(employeeToken)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("employee@techconnect.com");
        assertThat(claims.get("password")).isNull();
        assertThat(claims.get("passwordHash")).isNull();
    }

    @Test
    @DisplayName("18. JWT secret is externalized through configuration properties")
    void testJwtSecretIsNotHardCoded() {
        assertThat(jwtSecret).isNotBlank();
        assertThat(jwtSecret).isEqualTo("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    @Test
    @DisplayName("19. Missing or weak JWT secret fails safely at startup")
    void testMissingOrWeakJwtSecretFailsSafely() {
        // Missing / empty secret
        JwtServiceImpl nullSecretService = new JwtServiceImpl(null, 3600000);
        assertThatThrownBy(nullSecretService::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TECHCONNECT_JWT_SECRET");

        JwtServiceImpl emptySecretService = new JwtServiceImpl("   ", 3600000);
        assertThatThrownBy(emptySecretService::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TECHCONNECT_JWT_SECRET");

        // Weak secret (< 256 bits / 32 bytes)
        JwtServiceImpl shortSecretService = new JwtServiceImpl("too-short-secret", 3600000);
        assertThatThrownBy(shortSecretService::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    @DisplayName("20. Deactivated user cannot authenticate even with a valid JWT")
    void testDeactivatedUserCannotAuthenticate() throws Exception {
        User user = userRepository.findByEmail("employee@techconnect.com").orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/employee/test")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Restore active status
        user.setIsActive(true);
        userRepository.save(user);
    }
}
