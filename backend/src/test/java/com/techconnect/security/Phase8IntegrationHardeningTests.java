package com.techconnect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.*;
import com.techconnect.entity.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.*;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase8IntegrationHardeningTests {

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
    private SlaService slaService;

    @Value("${app.seed.dev-password:TestPassOnlyInTests!2026}")
    private String devPassword;

    private User employeeA;
    private User employeeB;
    private User engineerA;
    private User engineerB;
    private User managerA;
    private User managerB;
    private User admin;

    private String tokenEmployeeA;
    private String tokenEmployeeB;
    private String tokenEngineerA;
    private String tokenEngineerB;
    private String tokenManagerA;
    private String tokenManagerB;
    private String tokenAdmin;

    private Department deptOps;
    private Department deptSec;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE).orElseThrow();
        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER).orElseThrow();
        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        deptOps = departmentRepository.findByCode("IT-OPS").orElseThrow();
        deptSec = departmentRepository.findByCode("INFOSEC").orElseThrow();

        employeeA = userRepository.findByEmail("employee@techconnect.com").orElseThrow();
        engineerA = userRepository.findByEmail("engineer@techconnect.com").orElseThrow();
        managerA = userRepository.findByEmail("manager@techconnect.com").orElseThrow();
        admin = userRepository.findByEmail("admin@techconnect.com").orElseThrow();

        // Create isolated secondary users for IDOR testing
        employeeB = userRepository.findByEmail("employee.b@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("employee.b@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Employee")
                        .lastName("B")
                        .role(employeeRole)
                        .department(deptOps)
                        .isActive(true)
                        .build()));

        engineerB = userRepository.findByEmail("engineer.b@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("engineer.b@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Engineer")
                        .lastName("B")
                        .role(engineerRole)
                        .department(deptOps)
                        .isActive(true)
                        .build()));

        managerB = userRepository.findByEmail("manager.sec@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("manager.sec@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Manager")
                        .lastName("Security")
                        .role(managerRole)
                        .department(deptSec) // distinct department
                        .isActive(true)
                        .build()));

        tokenEmployeeA = jwtService.generateToken(employeeA.getEmail(), List.of("ROLE_EMPLOYEE"));
        tokenEmployeeB = jwtService.generateToken(employeeB.getEmail(), List.of("ROLE_EMPLOYEE"));
        tokenEngineerA = jwtService.generateToken(engineerA.getEmail(), List.of("ROLE_ENGINEER"));
        tokenEngineerB = jwtService.generateToken(engineerB.getEmail(), List.of("ROLE_ENGINEER"));
        tokenManagerA = jwtService.generateToken(managerA.getEmail(), List.of("ROLE_MANAGER"));
        tokenManagerB = jwtService.generateToken(managerB.getEmail(), List.of("ROLE_MANAGER"));
        tokenAdmin = jwtService.generateToken(admin.getEmail(), List.of("ROLE_ADMIN"));
    }

    private Ticket createTicketFor(User requester, Department dept, Priority priority, TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .title("Test Ticket for " + requester.getEmail())
                .description("Detailed test description for validation purposes")
                .category(TicketCategory.SOFTWARE)
                .priority(priority)
                .status(status)
                .createdBy(requester)
                .department(dept)
                .build();
        slaService.applySlaToNewTicket(ticket);
        return ticketRepository.save(ticket);
    }

    // =========================================================================
    // 1. IDOR / HORIZONTAL PRIVILEGE ESCALATION TESTS
    // =========================================================================

    @Test
    @DisplayName("IDOR: Employee A cannot access Employee B's ticket details (403)")
    void testEmployeeACannotAccessEmployeeBTicket() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.MEDIUM, TicketStatus.OPEN);

        mockMvc.perform(get("/api/tickets/" + ticketB.getId())
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("permission")));
    }

    @Test
    @DisplayName("IDOR: Employee A cannot access Employee B's ticket SLA metrics (403)")
    void testEmployeeACannotAccessEmployeeBSla() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.HIGH, TicketStatus.OPEN);

        mockMvc.perform(get("/api/tickets/" + ticketB.getId() + "/sla")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("IDOR: Employee A cannot view Employee B's ticket comments (403)")
    void testEmployeeACannotViewEmployeeBComments() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.LOW, TicketStatus.OPEN);

        mockMvc.perform(get("/api/tickets/" + ticketB.getId() + "/comments")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR: Employee A cannot add a comment to Employee B's ticket (403)")
    void testEmployeeACannotAddCommentToEmployeeBTicket() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.LOW, TicketStatus.OPEN);

        TicketCommentRequest req = TicketCommentRequest.builder()
                .content("Unauthorized comment attempt by Employee A")
                .isInternal(false)
                .build();

        mockMvc.perform(post("/api/tickets/" + ticketB.getId() + "/comments")
                        .header("Authorization", "Bearer " + tokenEmployeeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR: Employee A cannot access Employee B's ticket status history (403)")
    void testEmployeeACannotViewEmployeeBStatusHistory() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.LOW, TicketStatus.OPEN);

        mockMvc.perform(get("/api/tickets/" + ticketB.getId() + "/history")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR: Employee A cannot access Employee B's ticket assignments (403)")
    void testEmployeeACannotViewEmployeeBAssignments() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.LOW, TicketStatus.OPEN);

        mockMvc.perform(get("/api/tickets/" + ticketB.getId() + "/assignments")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR: Employee A cannot update status on Employee B's ticket (403)")
    void testEmployeeACannotUpdateEmployeeBStatus() throws Exception {
        Ticket ticketB = createTicketFor(employeeB, deptOps, Priority.MEDIUM, TicketStatus.RESOLVED);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.CLOSED)
                .reason("Attempting unauthorized close")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketB.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenEmployeeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. ENGINEER SCOPING & AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("Engineer A cannot access Engineer B's assigned IN_PROGRESS ticket (403)")
    void testEngineerACannotAccessEngineerBAssignedTicket() throws Exception {
        Ticket ticket = createTicketFor(employeeB, deptOps, Priority.HIGH, TicketStatus.IN_PROGRESS);
        ticket.setAssignedEngineer(engineerB);
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticket.getId())
                        .header("Authorization", "Bearer " + tokenEngineerA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer A cannot update status on Engineer B's assigned ticket (403)")
    void testEngineerACannotUpdateStatusOnEngineerBTicket() throws Exception {
        Ticket ticket = createTicketFor(employeeB, deptOps, Priority.HIGH, TicketStatus.IN_PROGRESS);
        ticket.setAssignedEngineer(engineerB);
        ticketRepository.save(ticket);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .resolutionDescription("Engineer A trying to resolve Engineer B's ticket")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenEngineerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engineer A can self-assign an unassigned OPEN ticket (200 OK)")
    void testEngineerASelfAssignOpenTicket() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.MEDIUM, TicketStatus.OPEN);

        TicketAssignmentRequest req = TicketAssignmentRequest.builder()
                .engineerId(engineerA.getId())
                .notes("Self assigning from queue")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignment")
                        .header("Authorization", "Bearer " + tokenEngineerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedEngineer.id").value(engineerA.getId()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("Engineer A cannot assign ticket to Engineer B (403 Forbidden)")
    void testEngineerACannotAssignToEngineerB() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.MEDIUM, TicketStatus.OPEN);

        TicketAssignmentRequest req = TicketAssignmentRequest.builder()
                .engineerId(engineerB.getId())
                .notes("Unauthorized delegation attempt")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/assignment")
                        .header("Authorization", "Bearer " + tokenEngineerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. MANAGER DEPARTMENTAL SCOPING
    // =========================================================================

    @Test
    @DisplayName("Manager B (Security) cannot view or assign ticket belonging to IT-OPS (403)")
    void testManagerCannotAccessOutOfDepartmentTicket() throws Exception {
        Ticket opsTicket = createTicketFor(employeeA, deptOps, Priority.HIGH, TicketStatus.IN_PROGRESS);
        opsTicket.setAssignedEngineer(engineerA);
        ticketRepository.save(opsTicket);

        // View attempt
        mockMvc.perform(get("/api/tickets/" + opsTicket.getId())
                        .header("Authorization", "Bearer " + tokenManagerB))
                .andExpect(status().isForbidden());

        // Assign attempt
        TicketAssignmentRequest assignReq = TicketAssignmentRequest.builder()
                .engineerId(engineerB.getId())
                .notes("Cross-department assign attempt")
                .build();

        mockMvc.perform(patch("/api/tickets/" + opsTicket.getId() + "/assignment")
                        .header("Authorization", "Bearer " + tokenManagerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. WORKFLOW STATE MACHINE INTEGRATION & VALIDATION
    // =========================================================================

    @Test
    @DisplayName("State Machine: Illegal transition OPEN -> CLOSED is rejected (400 Bad Request)")
    void testIllegalTransitionOpenToClosedRejected() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.MEDIUM, TicketStatus.OPEN);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.CLOSED)
                .reason("Direct close attempt from open")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid ticket status transition")));
    }

    @Test
    @DisplayName("State Machine: Illegal transition OPEN -> RESOLVED is rejected (400 Bad Request)")
    void testIllegalTransitionOpenToResolvedRejected() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.MEDIUM, TicketStatus.OPEN);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .resolutionDescription("Direct resolve without progress")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("State Machine: Employee can transition own RESOLVED ticket to CLOSED (200 OK)")
    void testEmployeeCanConfirmResolutionOfOwnTicket() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.LOW, TicketStatus.RESOLVED);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.CLOSED)
                .reason("Verified fixed on my machine")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenEmployeeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("State Machine: Employee can resume WAITING_FOR_USER ticket to IN_PROGRESS (200 OK)")
    void testEmployeeCanResumeWaitingTicket() throws Exception {
        Ticket ticket = createTicketFor(employeeA, deptOps, Priority.LOW, TicketStatus.WAITING_FOR_USER);

        TicketStatusUpdateRequest req = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .reason("Provided requested log files")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticket.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenEmployeeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // =========================================================================
    // 5. ROLE & SLA GOVERNANCE ACCESS
    // =========================================================================

    @Test
    @DisplayName("Role Security: Employee cannot access /api/sla/breached (403 Forbidden)")
    void testEmployeeCannotAccessBreachedTickets() throws Exception {
        mockMvc.perform(get("/api/sla/breached")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role Security: Employee cannot access /api/sla/summary (403 Forbidden)")
    void testEmployeeCannotAccessSlaSummary() throws Exception {
        mockMvc.perform(get("/api/sla/summary")
                        .header("Authorization", "Bearer " + tokenEmployeeA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role Security: Engineer cannot access /api/sla/breached (403 Forbidden)")
    void testEngineerCannotAccessBreachedTickets() throws Exception {
        mockMvc.perform(get("/api/sla/breached")
                        .header("Authorization", "Bearer " + tokenEngineerA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role Security: Manager and Admin can access /api/sla/summary (200 OK)")
    void testManagerAndAdminCanAccessSlaSummary() throws Exception {
        mockMvc.perform(get("/api/sla/summary")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveTickets").isNumber());

        mockMvc.perform(get("/api/sla/summary")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveTickets").isNumber());
    }

    // =========================================================================
    // 6. VALIDATION & ERROR RESPONSE STANDARDIZATION
    // =========================================================================

    @Test
    @DisplayName("Error Handling: Non-numeric ticket ID returns 400 Bad Request instead of 500")
    void testMethodArgumentTypeMismatchReturns400() throws Exception {
        mockMvc.perform(get("/api/tickets/invalid-id-string")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid parameter value for 'id'")));
    }

    @Test
    @DisplayName("Validation: Empty title and short description rejected with 400 Bad Request")
    void testCreateTicketValidationRejections() throws Exception {
        CreateTicketRequest invalidReq = CreateTicketRequest.builder()
                .title("")
                .description("abc")
                .category(TicketCategory.HARDWARE)
                .priority(Priority.LOW)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + tokenEmployeeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    // =========================================================================
    // 7. AUTH & JWT LIFECYCLE HARDENING
    // =========================================================================

    @Test
    @DisplayName("Auth: Missing Authorization header on protected route returns 401 Unauthorized")
    void testMissingAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Auth: Tampered or invalid JWT returns 401 Unauthorized")
    void testTamperedJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets/my")
                        .header("Authorization", "Bearer invalid.tampered.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Auth: Login with invalid password returns 401 Unauthorized")
    void testLoginWithInvalidPasswordReturns401() throws Exception {
        AuthLoginRequest req = AuthLoginRequest.builder()
                .email("employee@techconnect.com")
                .password("CompletelyWrongPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Auth: Public registration attempting ADMIN role is rejected with 400 Bad Request")
    void testPublicRegistrationPrivilegeEscalationRejected() throws Exception {
        AuthRegisterRequest req = AuthRegisterRequest.builder()
                .name("Attacker Admin")
                .email("attacker.admin@techconnect.com")
                .password("Password123!Secure")
                .role("ROLE_ADMIN")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Public registration cannot assign administrative")));
    }
}
