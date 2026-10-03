package com.techconnect.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.client.AiSupportCopilotClient;
import com.techconnect.dto.CreateTicketRequest;
import com.techconnect.dto.TicketResponse;
import com.techconnect.dto.resolution.ResolutionRetrievalMetaDto;
import com.techconnect.dto.resolution.ResolutionSourceDto;
import com.techconnect.dto.resolution.ResolutionSuggestionRequest;
import com.techconnect.dto.resolution.ResolutionSuggestionResponse;
import com.techconnect.dto.resolution.SimilarTicketDto;
import com.techconnect.entity.Department;
import com.techconnect.entity.Role;
import com.techconnect.entity.Team;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.DepartmentRepository;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.TeamRepository;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
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
public class Phase13AiResolutionAssistantTests {

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
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TicketService ticketService;

    @MockBean
    private AiSupportCopilotClient copilotClient;

    private User engineerUser;
    private User otherEngineerUser;
    private User employeeUser;
    private User managerUser;

    private String engineerToken;
    private String otherEngineerToken;
    private String employeeToken;
    private String managerToken;

    private Department dept;
    private Team team;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_EMPLOYEE)
                        .description("Standard Employee")
                        .build()));

        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_ENGINEER)
                        .description("Support Engineer")
                        .build()));

        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_MANAGER)
                        .description("Team Manager")
                        .build()));

        dept = departmentRepository.findByCode("TEST-DEPT-RES")
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .name("Resolution Test Dept")
                        .code("TEST-DEPT-RES")
                        .description("Department for Resolution Assistant tests")
                        .build()));

        team = teamRepository.findByNameAndDepartmentId("Resolution Team", dept.getId())
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name("Resolution Team")
                        .department(dept)
                        .description("Resolution Assistant Team")
                        .build()));

        engineerUser = userRepository.findByEmail("res.engineer1@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Res")
                        .lastName("Engineer1")
                        .email("res.engineer1@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(engineerRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        otherEngineerUser = userRepository.findByEmail("res.engineer2@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Res")
                        .lastName("Engineer2")
                        .email("res.engineer2@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(engineerRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        employeeUser = userRepository.findByEmail("res.employee1@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Res")
                        .lastName("Employee1")
                        .email("res.employee1@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(employeeRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        managerUser = userRepository.findByEmail("res.manager1@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .firstName("Res")
                        .lastName("Manager1")
                        .email("res.manager1@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .role(managerRole)
                        .department(dept)
                        .team(team)
                        .isActive(true)
                        .build()));

        engineerToken = jwtService.generateToken(engineerUser.getEmail(), List.of("ROLE_ENGINEER"));
        otherEngineerToken = jwtService.generateToken(otherEngineerUser.getEmail(), List.of("ROLE_ENGINEER"));
        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
        managerToken = jwtService.generateToken(managerUser.getEmail(), List.of("ROLE_MANAGER"));
    }

    @Test
    @DisplayName("1. Authorized Engineer receives grounded resolution suggestion with steps, sources, and similar tickets")
    void testResolutionSuggestion_Engineer_Success() throws Exception {
        // Create an open ticket assigned to engineerUser
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Cannot connect to corporate VPN after profile change")
                .description("User gets AnyConnect error 412 when attempting to authenticate.")
                .category(TicketCategory.VPN)
                .priority(Priority.HIGH)
                .build();
        TicketResponse createdTicket = ticketService.createTicket(createReq, employeeUser.getEmail());

        // Assign ticket to engineerUser
        Ticket ticketEntity = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        ticketEntity.setAssignedEngineer(engineerUser);
        ticketEntity.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticketEntity);

        // Mock AI Python response
        ResolutionSuggestionResponse mockResponse = ResolutionSuggestionResponse.builder()
                .ticketId(createdTicket.getId())
                .suggestion("Reset the user AnyConnect client profile and flush DNS cache.")
                .grounded(true)
                .steps(List.of(
                        "Flush local DNS cache via ipconfig /flushdns",
                        "Delete cached AnyConnect profile XML under ProgramData",
                        "Restart Cisco AnyConnect Secure Mobility Agent service"
                ))
                .sources(List.of(
                        ResolutionSourceDto.builder()
                                .type("KNOWLEDGE_ARTICLE")
                                .articleId("art-vpn-101")
                                .title("Cisco AnyConnect Troubleshooting")
                                .section("RESOLUTION")
                                .similarity(0.89)
                                .version(2)
                                .build()
                ))
                .similarTickets(List.of(
                        SimilarTicketDto.builder()
                                .ticketId(456L)
                                .similarity(0.84)
                                .category("VPN")
                                .priority("HIGH")
                                .build()
                ))
                .retrievalMeta(ResolutionRetrievalMetaDto.builder()
                        .topK(5)
                        .knowledgeChunksUsed(1)
                        .similarTicketsUsed(1)
                        .bestSimilarity(0.89)
                        .build())
                .model("grounded-extractive-v1")
                .provider("techconnect-resolution-synthesizer")
                .processingTimeMs(55)
                .build();

        when(copilotClient.generateResolutionSuggestion(any())).thenReturn(mockResponse);

        ResolutionSuggestionRequest request = ResolutionSuggestionRequest.builder()
                .topK(5)
                .minSimilarity(0.30)
                .build();

        mockMvc.perform(post("/api/ai/tickets/" + createdTicket.getId() + "/resolution-suggestion")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(createdTicket.getId()))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.suggestion").value("Reset the user AnyConnect client profile and flush DNS cache."))
                .andExpect(jsonPath("$.steps[0]").value("Flush local DNS cache via ipconfig /flushdns"))
                .andExpect(jsonPath("$.sources[0].title").value("Cisco AnyConnect Troubleshooting"))
                .andExpect(jsonPath("$.similarTickets[0].ticketId").value(456))
                .andExpect(jsonPath("$.retrievalMeta.knowledgeChunksUsed").value(1));
    }

    @Test
    @DisplayName("2. Employee role is strictly forbidden from engineer resolution endpoint (403 Forbidden)")
    void testResolutionSuggestion_Employee_Forbidden403() throws Exception {
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Employee created ticket")
                .description("Testing authorization barrier.")
                .category(TicketCategory.SOFTWARE)
                .priority(Priority.LOW)
                .build();
        TicketResponse createdTicket = ticketService.createTicket(createReq, employeeUser.getEmail());

        mockMvc.perform(post("/api/ai/tickets/" + createdTicket.getId() + "/resolution-suggestion")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Unauthenticated request to resolution endpoint returns 401 Unauthorized")
    void testResolutionSuggestion_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/ai/tickets/1/resolution-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. IDOR Protection: Engineer cannot access resolution assistant for ticket assigned to another engineer")
    void testResolutionSuggestion_IDOR_EngineerUnauthorized_Returns403() throws Exception {
        // Create ticket and assign to engineerUser
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Sensitive Server Ticket")
                .description("Critical server issue.")
                .category(TicketCategory.NETWORK)
                .priority(Priority.CRITICAL)
                .build();
        TicketResponse createdTicket = ticketService.createTicket(createReq, employeeUser.getEmail());

        Ticket ticketEntity = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        ticketEntity.setAssignedEngineer(engineerUser);
        ticketEntity.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticketEntity);

        // otherEngineer attempts to access ticket assigned to engineerUser
        mockMvc.perform(post("/api/ai/tickets/" + createdTicket.getId() + "/resolution-suggestion")
                        .header("Authorization", "Bearer " + otherEngineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Non-existent ticket returns 404 Not Found")
    void testResolutionSuggestion_NonExistentTicket_Returns404() throws Exception {
        mockMvc.perform(post("/api/ai/tickets/999999/resolution-suggestion")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Python AI service failure returns graceful ungrounded fallback response")
    void testResolutionSuggestion_PythonServiceUnavailable_ReturnsFallbackResponse() throws Exception {
        // Create ticket assigned to engineer
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Outlook search index corrupted")
                .description("Search results fail to return recent emails.")
                .category(TicketCategory.EMAIL)
                .priority(Priority.MEDIUM)
                .build();
        TicketResponse createdTicket = ticketService.createTicket(createReq, employeeUser.getEmail());

        Ticket ticketEntity = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        ticketEntity.setAssignedEngineer(engineerUser);
        ticketEntity.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticketEntity);

        // Mock fallback response from client
        when(copilotClient.generateResolutionSuggestion(any())).thenReturn(
                ResolutionSuggestionResponse.builder()
                        .ticketId(createdTicket.getId())
                        .grounded(false)
                        .suggestion("The AI Resolution Assistant is temporarily unavailable. Please refer to standard SOPs.")
                        .steps(Collections.emptyList())
                        .sources(Collections.emptyList())
                        .similarTickets(Collections.emptyList())
                        .provider("fallback")
                        .model("none")
                        .build()
        );

        mockMvc.perform(post("/api/ai/tickets/" + createdTicket.getId() + "/resolution-suggestion")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.suggestion").value("The AI Resolution Assistant is temporarily unavailable. Please refer to standard SOPs."))
                .andExpect(jsonPath("$.steps").isEmpty());
    }

    @Test
    @DisplayName("7. Manager can access resolution assistant for tickets in their department/team")
    void testResolutionSuggestion_Manager_Success() throws Exception {
        CreateTicketRequest createReq = CreateTicketRequest.builder()
                .title("Department Printer Driver Conflict")
                .description("Canon Copier duplex printing fails.")
                .category(TicketCategory.HARDWARE)
                .priority(Priority.LOW)
                .build();
        TicketResponse createdTicket = ticketService.createTicket(createReq, employeeUser.getEmail());

        Ticket ticketEntity = ticketRepository.findById(createdTicket.getId()).orElseThrow();
        ticketEntity.setDepartment(dept);
        ticketEntity.setAssignedTeam(team);
        ticketEntity.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticketEntity);

        when(copilotClient.generateResolutionSuggestion(any())).thenReturn(
                ResolutionSuggestionResponse.builder()
                        .ticketId(createdTicket.getId())
                        .grounded(true)
                        .suggestion("Reinstall the Canon Generic Plus PCL6 driver.")
                        .steps(List.of("Uninstall conflicting driver", "Install Generic Plus PCL6"))
                        .sources(Collections.emptyList())
                        .similarTickets(Collections.emptyList())
                        .build()
        );

        mockMvc.perform(post("/api/ai/tickets/" + createdTicket.getId() + "/resolution-suggestion")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.suggestion").value("Reinstall the Canon Generic Plus PCL6 driver."));
    }

    @Test
    @DisplayName("8. Direct client test on unreachable Python service returns graceful fallback")
    void testAiSupportCopilotClient_DirectUnreachable_ReturnsResolutionFallback() {
        AiSupportCopilotClient client = new AiSupportCopilotClient("http://localhost:59998", 500);
        ResolutionSuggestionResponse resp = client.generateResolutionSuggestion(Map.of("ticketId", 123L));

        assertThat(resp).isNotNull();
        assertThat(resp.isGrounded()).isFalse();
        assertThat(resp.getSuggestion()).contains("temporarily unavailable");
        assertThat(resp.getSteps()).isEmpty();
    }
}
