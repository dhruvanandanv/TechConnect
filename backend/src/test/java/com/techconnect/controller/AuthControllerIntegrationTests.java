package com.techconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.AuthLoginRequest;
import com.techconnect.dto.AuthRegisterRequest;
import com.techconnect.entity.User;
import com.techconnect.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("1. Should successfully register a new user with 201 Created and safe response")
    void testSuccessfulRegistration() throws Exception {
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("Alice Smith")
                .email("alice.smith@example.com")
                .password("SecurePassword123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.user.name").value("Alice Smith"))
                .andExpect(jsonPath("$.user.email").value("alice.smith@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_EMPLOYEE"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("2. Should reject duplicate email with 409 Conflict")
    void testDuplicateEmailRegistration() throws Exception {
        AuthRegisterRequest request1 = AuthRegisterRequest.builder()
                .name("Bob First")
                .email("bob.duplicate@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        AuthRegisterRequest request2 = AuthRegisterRequest.builder()
                .name("Bob Second")
                .email("bob.duplicate@example.com")
                .password("AnotherPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("3. Should reject invalid email format with 400 Bad Request")
    void testInvalidEmailRegistration() throws Exception {
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("Charlie")
                .email("invalid-email-format")
                .password("ValidPass123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. Should reject too short password with 400 Bad Request")
    void testInvalidPasswordRegistration() throws Exception {
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("David")
                .email("david@example.com")
                .password("short") // less than 8 chars
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Should verify password is encrypted with BCrypt in the database")
    void testPasswordIsHashedInDatabase() throws Exception {
        String rawPassword = "UniquePasswordToHash123!";
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("Eve Secure")
                .email("eve.hashed@example.com")
                .password(rawPassword)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Optional<User> userOpt = userRepository.findByEmail("eve.hashed@example.com");
        assertThat(userOpt).isPresent();
        User savedUser = userOpt.get();

        // Plaintext password must NOT be stored
        assertThat(savedUser.getPassword()).isNotEqualTo(rawPassword);
        // Must be BCrypt format ($2a$, $2b$, or $2y$)
        assertThat(savedUser.getPassword()).startsWith("$2");
        // Must match using PasswordEncoder
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("6. Should default public registration role to ROLE_EMPLOYEE when role field is omitted")
    void testPublicRegistrationDefaultsToEmployee() throws Exception {
        AuthRegisterRequest request = AuthRegisterRequest.builder()
                .name("Frank Default")
                .email("frank.default@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ROLE_EMPLOYEE"));
    }

    @Test
    @DisplayName("7. Should reject attempts by public registration to create ADMIN or MANAGER")
    void testPublicRegistrationCannotCreateAdmin() throws Exception {
        AuthRegisterRequest adminRequest = AuthRegisterRequest.builder()
                .name("Malicious Attacker")
                .email("attacker@example.com")
                .password("AttackPassword123!")
                .role("ROLE_ADMIN")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Public registration cannot assign administrative")));
    }

    @Test
    @DisplayName("8. Should successfully login with valid credentials")
    void testSuccessfulLogin() throws Exception {
        // Register a user first
        String email = "grace.login@example.com";
        String password = "LoginPassword123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthRegisterRequest.builder()
                                .name("Grace Hopper")
                                .email(email)
                                .password(password)
                                .build())))
                .andExpect(status().isCreated());

        // Attempt login
        AuthLoginRequest loginRequest = AuthLoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.name").value("Grace Hopper"))
                .andExpect(jsonPath("$.user.role").value("ROLE_EMPLOYEE"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("9. Should reject login with invalid password returning 401 Unauthorized")
    void testLoginWithInvalidPassword() throws Exception {
        String email = "helen.auth@example.com";
        String correctPassword = "CorrectPassword123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthRegisterRequest.builder()
                                .name("Helen")
                                .email(email)
                                .password(correctPassword)
                                .build())))
                .andExpect(status().isCreated());

        AuthLoginRequest loginRequest = AuthLoginRequest.builder()
                .email(email)
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("10. Should reject login with non-existent email returning 401 Unauthorized")
    void testLoginWithNonExistentEmail() throws Exception {
        AuthLoginRequest loginRequest = AuthLoginRequest.builder()
                .email("ghost.user@example.com")
                .password("AnyPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
