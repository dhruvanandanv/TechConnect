package com.techconnect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.client.AiTicketIntelligenceClient;
import com.techconnect.dto.*;
import com.techconnect.entity.Department;
import com.techconnect.entity.Role;
import com.techconnect.entity.Team;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.repository.DepartmentRepository;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.TeamRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.AiTicketIntelligenceService;
import com.techconnect.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase9TicketIntelligenceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AiTicketIntelligenceService aiTicketIntelligenceService;

    @MockBean
    private AiTicketIntelligenceClient aiClient;

    private User employeeUser;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_EMPLOYEE)
                        .description("Standard Employee")
                        .build()));

        Department dept = departmentRepository.findByCode("TEST-DEPT")
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .name("Test Department")
                        .code("TEST-DEPT")
                        .description("Test Department Description")
                        .build()));

        Team team = teamRepository.findByNameAndDepartmentId("Test Team", dept.getId())
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name("Test Team")
                        .department(dept)
                        .description("Test Team Description")
                        .build()));

        employeeUser = userRepository.findByEmail("ai.employee@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("AI")
                        .lastName("Employee")
                        .email("ai.employee@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(employeeRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
    }

    @Test
    @DisplayName("Authenticated user can successfully request AI ticket analysis")
    void testAnalyzeTicket_Authenticated_Success() throws Exception {
        AiAnalysisResponse mockResponse = AiAnalysisResponse.builder()
                .aiAvailable(true)
                .message("AI analysis completed successfully")
                .category(AiPredictionScore.builder().value("VPN").confidence(0.92).build())
                .priority(AiPredictionScore.builder().value("HIGH").confidence(0.88).build())
                .suggestedTeam(AiPredictionScore.builder().value("NETWORK_SUPPORT").confidence(0.85).build())
                .summary("User is unable to connect to the corporate VPN.")
                .reasons(List.of("Detected VPN connectivity terminology", "High-impact network keywords identified"))
                .modelVersion("ticket-intelligence-v1")
                .processingTimeMs(24)
                .build();

        when(aiClient.analyzeTicket(any(AiAnalysisRequest.class))).thenReturn(mockResponse);

        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .title("Cannot connect to AnyConnect VPN")
                .description("VPN client reports gateway timeout when trying to authenticate.")
                .build();

        mockMvc.perform(post("/api/tickets/analyze")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAvailable").value(true))
                .andExpect(jsonPath("$.category.value").value("VPN"))
                .andExpect(jsonPath("$.category.confidence").value(0.92))
                .andExpect(jsonPath("$.priority.value").value("HIGH"))
                .andExpect(jsonPath("$.suggested_team.value").value("NETWORK_SUPPORT"))
                .andExpect(jsonPath("$.summary").value("User is unable to connect to the corporate VPN."))
                .andExpect(jsonPath("$.model_version").value("ticket-intelligence-v1"));
    }

    @Test
    @DisplayName("Unauthenticated request to analyze endpoint returns 401 Unauthorized")
    void testAnalyzeTicket_Unauthenticated_Returns401() throws Exception {
        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .title("Unauthorized Title")
                .description("Unauthorized description text here")
                .build();

        mockMvc.perform(post("/api/tickets/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid request payload (blank title/description) returns 400 Bad Request")
    void testAnalyzeTicket_ValidationFailure() throws Exception {
        AiAnalysisRequest badRequest = AiAnalysisRequest.builder()
                .title("  ")
                .description("Short")
                .build();

        mockMvc.perform(post("/api/tickets/analyze")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When AI service is offline, analyze returns graceful unavailable response without error")
    void testAnalyzeTicket_AiServiceUnavailable_FallsBackGracefully() throws Exception {
        when(aiClient.analyzeTicket(any(AiAnalysisRequest.class)))
                .thenReturn(AiAnalysisResponse.unavailable("AI analysis service is unreachable or timed out"));

        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .title("Laptop won't boot up")
                .description("Black screen upon pressing power button and fan is spinning.")
                .build();

        mockMvc.perform(post("/api/tickets/analyze")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAvailable").value(false))
                .andExpect(jsonPath("$.message").value("AI analysis service is unreachable or timed out"));
    }

    @Test
    @DisplayName("Ticket creation succeeds normally and remains authoritative regardless of AI availability")
    void testTicketCreation_HumanInputIsAuthoritative() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("VPN dropping every 5 minutes")
                .description("Network tunnel disconnects when running large database exports.")
                .category(TicketCategory.SOFTWARE) // Human deliberately selects SOFTWARE
                .priority(Priority.LOW)            // Human deliberately selects LOW
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("VPN dropping every 5 minutes"))
                .andExpect(jsonPath("$.category").value("SOFTWARE")) // Authoritative human value preserved
                .andExpect(jsonPath("$.priority").value("LOW"))       // Authoritative human value preserved
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("AiTicketIntelligenceService handles null or empty client response safely")
    void testAiTicketIntelligenceService_FallbackDirect() {
        when(aiClient.analyzeTicket(any(AiAnalysisRequest.class)))
                .thenReturn(AiAnalysisResponse.unavailable("Service offline"));

        AiAnalysisRequest req = AiAnalysisRequest.builder()
                .title("Printer jam")
                .description("Tray 2 jam on 4th floor canon printer")
                .build();

        AiAnalysisResponse response = aiTicketIntelligenceService.analyzeTicket(req);
        assertThat(response).isNotNull();
        assertThat(response.isAiAvailable()).isFalse();
        assertThat(response.getMessage()).contains("Service offline");
    }
}
