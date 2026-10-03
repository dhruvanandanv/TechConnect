package com.techconnect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.client.AiSupportCopilotClient;
import com.techconnect.dto.CreateTicketRequest;
import com.techconnect.dto.TicketResponse;
import com.techconnect.dto.copilot.CopilotAnswerRequest;
import com.techconnect.dto.copilot.CopilotAnswerResponse;
import com.techconnect.dto.copilot.CopilotRetrievalMetaDto;
import com.techconnect.dto.copilot.CopilotSourceChunkDto;
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
import com.techconnect.service.AiSupportCopilotService;
import com.techconnect.service.JwtService;
import com.techconnect.service.TicketService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase12AiSupportCopilotTests {

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
    private TicketService ticketService;

    @Autowired
    private AiSupportCopilotService copilotService;

    @MockBean
    private AiSupportCopilotClient copilotClient;

    private User employeeUser;
    private User otherEmployeeUser;
    private String employeeToken;
    private String otherEmployeeToken;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_EMPLOYEE)
                        .description("Standard Employee")
                        .build()));

        Department dept = departmentRepository.findByCode("TEST-DEPT-COPILOT")
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .name("Copilot Test Dept")
                        .code("TEST-DEPT-COPILOT")
                        .description("Department for Copilot tests")
                        .build()));

        Team team = teamRepository.findByNameAndDepartmentId("Copilot Team", dept.getId())
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name("Copilot Team")
                        .department(dept)
                        .description("Copilot Team Description")
                        .build()));

        employeeUser = userRepository.findByEmail("copilot.employee1@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Copilot")
                        .lastName("Employee1")
                        .email("copilot.employee1@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(employeeRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        otherEmployeeUser = userRepository.findByEmail("copilot.employee2@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Copilot")
                        .lastName("Employee2")
                        .email("copilot.employee2@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(employeeRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
        otherEmployeeToken = jwtService.generateToken(otherEmployeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
    }

    @Test
    @DisplayName("1. Authenticated user receives grounded answer and citations")
    void testCopilotAnswer_Authenticated_Success() throws Exception {
        CopilotSourceChunkDto chunk = CopilotSourceChunkDto.builder()
                .articleId("art-vpn-1")
                .chunkId("art-vpn-1-res-0")
                .articleVersion(1)
                .title("Corporate VPN Setup")
                .section("RESOLUTION")
                .content("Flush DNS and restart Cisco AnyConnect service.")
                .similarity(0.8842)
                .category("VPN")
                .tags(List.of("vpn", "cisco"))
                .build();

        CopilotAnswerResponse mockResponse = CopilotAnswerResponse.builder()
                .answer("Based on the TechConnect knowledge base, flush DNS and restart Cisco AnyConnect.")
                .grounded(true)
                .confidence(0.8842)
                .sources(List.of(chunk))
                .retrieval(CopilotRetrievalMetaDto.builder()
                        .topK(5)
                        .resultsUsed(1)
                        .bestSimilarity(0.8842)
                        .build())
                .retrievedChunks(1)
                .model("grounded-extractive-v1")
                .provider("techconnect-grounded-synthesizer")
                .processingTimeMs(45)
                .build();

        when(copilotClient.generateAnswer(any())).thenReturn(mockResponse);

        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("My corporate VPN keeps disconnecting from home")
                .category(TicketCategory.VPN)
                .topK(5)
                .minSimilarity(0.30)
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.confidence").value(0.8842))
                .andExpect(jsonPath("$.answer").value("Based on the TechConnect knowledge base, flush DNS and restart Cisco AnyConnect."))
                .andExpect(jsonPath("$.sources[0].title").value("Corporate VPN Setup"))
                .andExpect(jsonPath("$.sources[0].section").value("RESOLUTION"))
                .andExpect(jsonPath("$.sources[0].similarity").value(0.8842))
                .andExpect(jsonPath("$.retrieval.bestSimilarity").value(0.8842));
    }

    @Test
    @DisplayName("2. Unauthenticated request to Copilot endpoint is rejected with 401")
    void testCopilotAnswer_Unauthenticated_Returns401() throws Exception {
        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("How do I fix my VPN?")
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Python RAG unavailable returns graceful non-grounded fallback response")
    void testCopilotAnswer_PythonRagUnavailable_ReturnsFallbackResponse() throws Exception {
        when(copilotClient.generateAnswer(any())).thenReturn(
                CopilotAnswerResponse.unavailable("The AI Support Copilot is temporarily unavailable. You can still use Knowledge Base search.")
        );

        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("How do I update printer driver?")
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.answer").value("The AI Support Copilot is temporarily unavailable. You can still use Knowledge Base search."))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    @DisplayName("4. Empty query is rejected with 400 Bad Request")
    void testCopilotAnswer_EmptyQuery_RejectedWith400() throws Exception {
        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("   ")
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Query exceeding 1000 characters is rejected with 400 Bad Request")
    void testCopilotAnswer_QueryTooLong_RejectedWith400() throws Exception {
        String longQuery = "X".repeat(1001);
        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query(longQuery)
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Valid ticket context is successfully attached to Copilot inquiry")
    void testCopilotAnswer_TicketContext_ValidAccess() throws Exception {
        // Create ticket owned by employeeUser
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("VPN Gateway Timeout")
                .description("Cannot establish remote connection from home.")
                .category(TicketCategory.VPN)
                .priority(Priority.HIGH)
                .build();
        TicketResponse ticket = ticketService.createTicket(createReq, employeeUser.getEmail());

        CopilotAnswerResponse mockResponse = CopilotAnswerResponse.builder()
                .answer("Try restarting your VPN client.")
                .grounded(true)
                .confidence(0.85)
                .sources(Collections.emptyList())
                .retrievedChunks(1)
                .model("test-model")
                .provider("test-provider")
                .build();

        when(copilotClient.generateAnswer(any())).thenReturn(mockResponse);

        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("What should I do next?")
                .ticketId(ticket.getId())
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.ticketTitle").value("VPN Gateway Timeout"));
    }

    @Test
    @DisplayName("7. Ticket IDOR Protection: Employee cannot query ticket owned by another employee")
    void testCopilotAnswer_TicketContext_IDOR_ThrowsForbidden() throws Exception {
        // Ticket owned by otherEmployeeUser
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Private Confidential Ticket")
                .description("Sensitive departmental issue.")
                .category(TicketCategory.OTHER)
                .priority(Priority.MEDIUM)
                .build();
        TicketResponse otherTicket = ticketService.createTicket(createReq, otherEmployeeUser.getEmail());

        // employeeUser attempts to access otherEmployeeUser's ticket
        CopilotAnswerRequest request = CopilotAnswerRequest.builder()
                .query("What are the notes on this ticket?")
                .ticketId(otherTicket.getId())
                .build();

        mockMvc.perform(post("/api/ai/copilot/answer")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. AiSupportCopilotClient direct unit test catches unreachable service gracefully")
    void testAiSupportCopilotClient_DirectUnreachable_ReturnsFallback() {
        AiSupportCopilotClient client = new AiSupportCopilotClient("http://localhost:59998", 500);
        CopilotAnswerResponse resp = client.generateAnswer(Map.of("query", "test query"));

        assertThat(resp).isNotNull();
        assertThat(resp.isGrounded()).isFalse();
        assertThat(resp.getAnswer()).contains("temporarily unavailable");
        assertThat(resp.getSources()).isEmpty();
    }
}
