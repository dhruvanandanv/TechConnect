package com.techconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.CreateTicketRequest;
import com.techconnect.dto.TicketAssignmentRequest;
import com.techconnect.dto.TicketResponse;
import com.techconnect.dto.TicketStatusUpdateRequest;
import com.techconnect.entity.Role;
import com.techconnect.entity.SLA;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.SlaStatus;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.SLARepository;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.JwtService;
import com.techconnect.service.SlaService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private SLARepository slaRepository;

    @Autowired
    private SlaService slaService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${app.seed.dev-password:TestPassOnlyInTests!2026}")
    private String devPassword;

    private String employeeToken;
    private String employee2Token;
    private String engineerToken;
    private String managerToken;
    private String adminToken;

    private User employeeUser;
    private User employee2User;
    private User engineerUser;
    private User managerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE).orElseThrow();
        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER).orElseThrow();
        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        employeeUser = userRepository.findByEmail("employee@techconnect.com").orElseThrow();
        engineerUser = userRepository.findByEmail("engineer@techconnect.com").orElseThrow();
        managerUser = userRepository.findByEmail("manager@techconnect.com").orElseThrow();
        adminUser = userRepository.findByEmail("admin@techconnect.com").orElseThrow();

        employee2User = userRepository.findByEmail("employee2@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("employee2@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Bob")
                        .lastName("Employee")
                        .role(employeeRole)
                        .isActive(true)
                        .build()));

        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
        employee2Token = jwtService.generateToken(employee2User.getEmail(), List.of("ROLE_EMPLOYEE"));
        engineerToken = jwtService.generateToken(engineerUser.getEmail(), List.of("ROLE_ENGINEER"));
        managerToken = jwtService.generateToken(managerUser.getEmail(), List.of("ROLE_MANAGER"));
        adminToken = jwtService.generateToken(adminUser.getEmail(), List.of("ROLE_ADMIN"));
    }

    private Ticket createTicketViaApi(Priority priority, String token) throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("Ticket for SLA testing - " + priority)
                .description("Detailed description for SLA priority test")
                .category(TicketCategory.SOFTWARE)
                .priority(priority)
                .build();

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TicketResponse res = objectMapper.readValue(result.getResponse().getContentAsString(), TicketResponse.class);
        return ticketRepository.findById(res.getId()).orElseThrow();
    }

    // =========================================================================
    // 1. SLA ASSIGNMENT & DEADLINES (Scenarios 1 - 6)
    // =========================================================================

    @Test
    @DisplayName("1. New LOW ticket receives LOW SLA policy")
    void testNewLowTicketReceivesLowSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.LOW, employeeToken);

        assertThat(ticket.getSla()).isNotNull();
        assertThat(ticket.getSla().getPriority()).isEqualTo(Priority.LOW);
        assertThat(ticket.getResponseDeadline()).isNotNull();
        assertThat(ticket.getSlaDeadline()).isNotNull();
    }

    @Test
    @DisplayName("2. New MEDIUM ticket receives MEDIUM SLA policy")
    void testNewMediumTicketReceivesMediumSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.MEDIUM, employeeToken);

        assertThat(ticket.getSla()).isNotNull();
        assertThat(ticket.getSla().getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(ticket.getResponseDeadline()).isNotNull();
        assertThat(ticket.getSlaDeadline()).isNotNull();
    }

    @Test
    @DisplayName("3. New HIGH ticket receives HIGH SLA policy")
    void testNewHighTicketReceivesHighSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);

        assertThat(ticket.getSla()).isNotNull();
        assertThat(ticket.getSla().getPriority()).isEqualTo(Priority.HIGH);
        assertThat(ticket.getResponseDeadline()).isNotNull();
        assertThat(ticket.getSlaDeadline()).isNotNull();
    }

    @Test
    @DisplayName("4. New CRITICAL ticket receives CRITICAL SLA policy")
    void testNewCriticalTicketReceivesCriticalSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.CRITICAL, employeeToken);

        assertThat(ticket.getSla()).isNotNull();
        assertThat(ticket.getSla().getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(ticket.getResponseDeadline()).isNotNull();
        assertThat(ticket.getSlaDeadline()).isNotNull();
    }

    @Test
    @DisplayName("5. Response deadline is calculated correctly according to policy hours")
    void testResponseDeadlineCalculatedCorrectly() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        SLA sla = slaRepository.findByPriority(Priority.HIGH).orElseThrow();

        LocalDateTime expectedResponse = ticket.getCreatedAt().plusHours(sla.getResponseTimeHours());
        assertThat(ticket.getResponseDeadline().withNano(0))
                .isEqualTo(expectedResponse.withNano(0));
    }

    @Test
    @DisplayName("6. Resolution deadline is calculated correctly according to policy hours")
    void testResolutionDeadlineCalculatedCorrectly() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        SLA sla = slaRepository.findByPriority(Priority.HIGH).orElseThrow();

        LocalDateTime expectedResolution = ticket.getCreatedAt().plusHours(sla.getResolutionTimeHours());
        assertThat(ticket.getSlaDeadline().withNano(0))
                .isEqualTo(expectedResolution.withNano(0));
    }

    // =========================================================================
    // 2. RESPONSE SLA (Scenarios 7 - 9)
    // =========================================================================

    @Test
    @DisplayName("7. Engineer assignment completes response milestone and marks response COMPLETED")
    void testEngineerAssignmentCompletesResponseSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        assertThat(ticket.getRespondedAt()).isNull();

        // Manager assigns engineer
        TicketAssignmentRequest assignReq = TicketAssignmentRequest.builder()
                .engineerId(engineerUser.getId())
                .notes("Assigning engineer for investigation")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignment")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        Ticket updated = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(updated.getRespondedAt()).isNotNull();

        // Check SLA endpoint
        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.remainingResponseMinutes").value(0));
    }

    @Test
    @DisplayName("8. Response within deadline is marked successful (COMPLETED)")
    void testResponseWithinDeadlineIsSuccessful() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.MEDIUM, employeeToken);

        // Transition to IN_PROGRESS by engineer (self-assign first)
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest statusReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .reason("Starting diagnostic tests")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("9. Late response is marked BREACHED")
    void testLateResponseIsMarkedBreached() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.LOW, employeeToken);

        // Set response deadline in the past
        ticket.setResponseDeadline(LocalDateTime.now().minusHours(2));
        ticketRepository.save(ticket);

        // Engineer now responds after deadline
        slaService.recordResponseMilestone(ticket, engineerUser);
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("BREACHED"));
    }

    // =========================================================================
    // 3. RESOLUTION SLA (Scenarios 10 - 12)
    // =========================================================================

    @Test
    @DisplayName("10. RESOLVED ticket completes resolution SLA")
    void testResolvedTicketCompletesResolutionSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest resolveReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .resolutionDescription("Issue successfully fixed by clearing cache")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.remainingResolutionMinutes").value(0))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    @DisplayName("11. Resolution within deadline is successful (COMPLETED)")
    void testResolutionWithinDeadlineIsSuccessful() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.MEDIUM, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest resolveReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .resolutionDescription("Fixed on time")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("12. Late resolution is marked BREACHED")
    void testLateResolutionIsBreached() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.LOW, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        // Set resolution deadline in past
        ticket.setSlaDeadline(LocalDateTime.now().minusHours(3));
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest resolveReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .resolutionDescription("Fixed after deadline")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("BREACHED"));
    }

    // =========================================================================
    // 4. STATUS INTEGRATION & PAUSE/RESUME (Scenarios 13 - 15)
    // =========================================================================

    @Test
    @DisplayName("13. Transitioning to WAITING_FOR_USER pauses the SLA clock")
    void testWaitingForUserPausesSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest pauseReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.WAITING_FOR_USER)
                .reason("Need additional diagnostics logs from user")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pauseReq)))
                .andExpect(status().isOk());

        Ticket paused = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(paused.getSlaPausedAt()).isNotNull();

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPaused").value(true))
                .andExpect(jsonPath("$.resolutionStatus").value("PAUSED"));
    }

    @Test
    @DisplayName("14. Returning to IN_PROGRESS resumes SLA and extends resolution deadline")
    void testReturningToInProgressResumesSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticket.setStatus(TicketStatus.WAITING_FOR_USER);
        // Simulate ticket paused 30 minutes ago
        ticket.setSlaPausedAt(LocalDateTime.now().minusMinutes(30));
        LocalDateTime originalDeadline = ticket.getSlaDeadline();
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest resumeReq = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .reason("User provided logs; resuming investigation")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resumeReq)))
                .andExpect(status().isOk());

        Ticket resumed = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(resumed.getSlaPausedAt()).isNull();
        assertThat(resumed.getTotalPausedDurationMinutes()).isGreaterThanOrEqualTo(29L);
        assertThat(resumed.getSlaDeadline()).isAfter(originalDeadline);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPaused").value(false))
                .andExpect(jsonPath("$.resolutionStatus").value("ON_TRACK"));
    }

    @Test
    @DisplayName("15. Existing ticket state transitions continue working seamlessly")
    void testExistingTicketTransitionsStillWork() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.MEDIUM, employeeToken);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);

        // Assign
        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignment")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TicketAssignmentRequest.builder()
                                .engineerId(engineerUser.getId()).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // In Progress
        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TicketStatusUpdateRequest.builder()
                                .status(TicketStatus.IN_PROGRESS).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Resolved
        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TicketStatusUpdateRequest.builder()
                                .status(TicketStatus.RESOLVED)
                                .resolutionDescription("Done").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Closed by Employee
        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TicketStatusUpdateRequest.builder()
                                .status(TicketStatus.CLOSED)
                                .reason("Confirmed working").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // =========================================================================
    // 5. VISIBILITY & AUTHORIZATION (Scenarios 16 - 21)
    // =========================================================================

    @Test
    @DisplayName("16. Employee can view SLA metrics for their own ticket")
    void testEmployeeSeesOwnTicketSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.responseDeadline").isNotEmpty())
                .andExpect(jsonPath("$.resolutionDeadline").isNotEmpty());
    }

    @Test
    @DisplayName("17. Employee cannot view SLA metrics for another user's ticket (403 Forbidden)")
    void testEmployeeCannotSeeAnotherUsersTicketSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employee2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("18. Engineer can view SLA metrics for assigned ticket")
    void testEngineerSeesAssignedTicketSla() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        ticket.setAssignedEngineer(engineerUser);
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId()));
    }

    @Test
    @DisplayName("19. Manager can access breached tickets endpoint (200 OK)")
    void testManagerCanAccessBreachedTickets() throws Exception {
        mockMvc.perform(get("/api/sla/breached")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("20. Employee cannot access breached tickets endpoint (403 Forbidden)")
    void testEmployeeCannotAccessBreachedTickets() throws Exception {
        mockMvc.perform(get("/api/sla/breached")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("21. Manager can access SLA summary dashboard endpoint (200 OK)")
    void testManagerCanAccessSlaSummary() throws Exception {
        createTicketViaApi(Priority.HIGH, employeeToken);

        mockMvc.perform(get("/api/sla/summary")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveTickets", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.onTrack").isNumber())
                .andExpect(jsonPath("$.atRisk").isNumber())
                .andExpect(jsonPath("$.breached").isNumber());
    }

    // =========================================================================
    // 6. DYNAMIC STATUS & THRESHOLDS (Scenarios 22 - 26)
    // =========================================================================

    @Test
    @DisplayName("22. On-track ticket is evaluated as ON_TRACK")
    void testDynamicStatus_OnTrack() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.LOW, employeeToken);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("ON_TRACK"));
    }

    @Test
    @DisplayName("23. Near-deadline ticket (<20% remaining) is evaluated as AT_RISK")
    void testDynamicStatus_AtRisk() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        // High SLA is 24 hours. Set remaining time to 2 hours (< 4.8 hours = 20%)
        ticket.setSlaDeadline(LocalDateTime.now().plusHours(2));
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("AT_RISK"))
                .andExpect(jsonPath("$.overallStatus").value("AT_RISK"));
    }

    @Test
    @DisplayName("24. Past deadline ticket is evaluated as BREACHED")
    void testDynamicStatus_Breached() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.HIGH, employeeToken);
        // Resolution deadline in past
        ticket.setSlaDeadline(LocalDateTime.now().minusHours(1));
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionStatus").value("BREACHED"))
                .andExpect(jsonPath("$.overallStatus").value("BREACHED"));
    }

    @Test
    @DisplayName("25. Admin has full visibility across all SLA endpoints")
    void testAdminHasFullSlaVisibility() throws Exception {
        Ticket ticket = createTicketViaApi(Priority.CRITICAL, employeeToken);

        mockMvc.perform(get("/api/tickets/" + ticket.getId() + "/sla")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sla/breached")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sla/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("26. SLA query for non-existent ticket returns 404 Not Found")
    void testNonExistentTicketSlaReturns404() throws Exception {
        mockMvc.perform(get("/api/tickets/999999/sla")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
