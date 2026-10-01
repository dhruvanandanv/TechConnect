package com.techconnect.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.dto.*;
import com.techconnect.entity.Role;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.TicketAssignment;
import com.techconnect.entity.TicketComment;
import com.techconnect.entity.TicketStatusHistory;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.*;
import com.techconnect.service.JwtService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerIntegrationTests {

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
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TicketStatusHistoryRepository ticketStatusHistoryRepository;

    @Autowired
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${app.seed.dev-password:TestPassOnlyInTests!2026}")
    private String devPassword;

    private String employeeToken;
    private String employee2Token;
    private String engineerToken;
    private String engineer2Token;
    private String managerToken;
    private String adminToken;

    private User employeeUser;
    private User employee2User;
    private User engineerUser;
    private User engineer2User;
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

        // Create second employee for multi-user isolation tests
        employee2User = userRepository.findByEmail("employee2@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("employee2@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Bob")
                        .lastName("Employee")
                        .role(employeeRole)
                        .isActive(true)
                        .build()));

        // Create second engineer for assignment tests
        engineer2User = userRepository.findByEmail("engineer2@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("engineer2@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Charlie")
                        .lastName("Engineer")
                        .role(engineerRole)
                        .team(engineerUser.getTeam())
                        .department(engineerUser.getDepartment())
                        .isActive(true)
                        .build()));

        // Generate JWTs for all actors
        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
        employee2Token = jwtService.generateToken(employee2User.getEmail(), List.of("ROLE_EMPLOYEE"));
        engineerToken = jwtService.generateToken(engineerUser.getEmail(), List.of("ROLE_ENGINEER"));
        engineer2Token = jwtService.generateToken(engineer2User.getEmail(), List.of("ROLE_ENGINEER"));
        managerToken = jwtService.generateToken(managerUser.getEmail(), List.of("ROLE_MANAGER"));
        adminToken = jwtService.generateToken(adminUser.getEmail(), List.of("ROLE_ADMIN"));
    }

    // =========================================================================
    // 1. TICKET CREATION (Scenarios 1 - 6)
    // =========================================================================

    @Test
    @DisplayName("1. Employee creates a ticket successfully (201 Created)")
    void testEmployeeCreatesTicketSuccessfully() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("VPN connection drops intermittently")
                .description("Whenever I connect to the corporate network from home, the VPN disconnects after 10 minutes.")
                .category(TicketCategory.VPN)
                .priority(Priority.HIGH)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("VPN connection drops intermittently"))
                .andExpect(jsonPath("$.category").value("VPN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requester.email").value("employee@techconnect.com"))
                .andExpect(jsonPath("$.requester.name").value("John Doe"))
                .andExpect(jsonPath("$.assignedEngineer").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    @DisplayName("2. Missing title is rejected with 400 Bad Request")
    void testCreateTicketMissingTitle() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("   ") // blank title
                .description("Valid description with enough characters")
                .category(TicketCategory.HARDWARE)
                .priority(Priority.MEDIUM)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("3. Missing description is rejected with 400 Bad Request")
    void testCreateTicketMissingDescription() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("Valid Ticket Title")
                .description("") // blank description
                .category(TicketCategory.SOFTWARE)
                .priority(Priority.LOW)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("4. Invalid category format is rejected with 400 Bad Request")
    void testCreateTicketInvalidCategory() throws Exception {
        String invalidPayload = """
                {
                    "title": "Valid Ticket Title",
                    "description": "Valid description with sufficient details",
                    "category": "INVALID_CATEGORY_NAME",
                    "priority": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Invalid priority format is rejected with 400 Bad Request")
    void testCreateTicketInvalidPriority() throws Exception {
        String invalidPayload = """
                {
                    "title": "Valid Ticket Title",
                    "description": "Valid description with sufficient details",
                    "category": "NETWORK",
                    "priority": "SUPER_DUPER_URGENT"
                }
                """;

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Requester identity automatically derived from authenticated JWT context")
    void testRequesterComesFromAuthenticatedUser() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title("Keyboard key 'E' is physically broken")
                .description("Need keyboard replacement for workstation.")
                .category(TicketCategory.HARDWARE)
                .priority(Priority.MEDIUM)
                .build();

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseNode.get("requester").get("email").asText()).isEqualTo("employee@techconnect.com");
        assertThat(responseNode.get("requester").get("id").asLong()).isEqualTo(employeeUser.getId());
    }

    // =========================================================================
    // 2. VISIBILITY & SCOPING (Scenarios 7 - 12)
    // =========================================================================

    @Test
    @DisplayName("7. Employee can view their own tickets via /api/tickets/my")
    void testEmployeeSeesOwnTickets() throws Exception {
        createTicketAs("Employee's First Issue", "Description 1", employeeToken);
        createTicketAs("Employee's Second Issue", "Description 2", employeeToken);

        mockMvc.perform(get("/api/tickets/my")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].requesterEmail").value("employee@techconnect.com"));
    }

    @Test
    @DisplayName("8. Employee cannot view another employee's private ticket (403 Forbidden)")
    void testEmployeeCannotViewAnotherEmployeesTicket() throws Exception {
        Long emp2TicketId = createTicketAs("Bob's Confidential Ticket", "Secret description", employee2Token);

        // Employee 1 attempts to access Employee 2's ticket
        mockMvc.perform(get("/api/tickets/" + emp2TicketId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("permission to access ticket")));
    }

    @Test
    @DisplayName("9. Engineer can view tickets assigned to them")
    void testEngineerSeesAssignedTicket() throws Exception {
        Long ticketId = createTicketAs("Server Down", "Investigate server outage", employeeToken);

        // Assign to engineerUser
        assignTicketAs(ticketId, engineerUser.getId(), adminToken);

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.assignedEngineer.email").value("engineer@techconnect.com"));
    }

    @Test
    @DisplayName("10. Engineer cannot view another engineer's assigned restricted ticket (403 Forbidden)")
    void testEngineerCannotAccessUnrelatedRestrictedTicket() throws Exception {
        Long ticketId = createTicketAs("Network Switch Configuration", "Secret switch configuration", employeeToken);

        // Assign to engineerUser and move to IN_PROGRESS
        assignTicketAs(ticketId, engineerUser.getId(), adminToken);
        updateStatusAs(ticketId, TicketStatus.IN_PROGRESS, null, engineerToken);

        // Engineer 2 attempts to view Engineer 1's in-progress ticket
        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + engineer2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("11. Manager can view team tickets")
    void testManagerSeesPermittedTeamTickets() throws Exception {
        Long ticketId = createTicketAs("Team printer broken", "Printer in IT area", employeeToken);

        // Assign ticket to manager's team
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setAssignedTeam(managerUser.getTeam());
        ticketRepository.save(ticket);

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId));
    }

    @Test
    @DisplayName("12. Admin can view all tickets across the entire organization")
    void testAdminCanViewAllTickets() throws Exception {
        Long t1 = createTicketAs("Ticket by Emp 1", "Desc 1", employeeToken);
        Long t2 = createTicketAs("Ticket by Emp 2", "Desc 2", employee2Token);

        // Admin can view t1
        mockMvc.perform(get("/api/tickets/" + t1)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(t1));

        // Admin can view t2
        mockMvc.perform(get("/api/tickets/" + t2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(t2));

        // Admin gets all tickets via /api/tickets/my
        mockMvc.perform(get("/api/tickets/my")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // =========================================================================
    // 3. STATUS TRANSITIONS & WORKFLOW (Scenarios 13 - 16)
    // =========================================================================

    @Test
    @DisplayName("13. Valid status transition succeeds (ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED)")
    void testValidStatusTransitionWorkflow() throws Exception {
        Long ticketId = createTicketAs("Software license request", "Need IntelliJ license", employeeToken);

        // 1. Assign to engineer
        assignTicketAs(ticketId, engineerUser.getId(), managerToken);

        // 2. Engineer starts work: ASSIGNED -> IN_PROGRESS
        updateStatusAs(ticketId, TicketStatus.IN_PROGRESS, "Working on provisioning", engineerToken);

        // 3. Engineer resolves: IN_PROGRESS -> RESOLVED
        updateStatusAs(ticketId, TicketStatus.RESOLVED, "License allocated and email sent", engineerToken);

        Ticket resolvedTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(resolvedTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolvedTicket.getResolvedAt()).isNotNull();

        // 4. Employee confirms resolution: RESOLVED -> CLOSED
        updateStatusAs(ticketId, TicketStatus.CLOSED, "Verified license works", employeeToken);

        Ticket closedTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(closedTicket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    @DisplayName("14. Invalid status transition is rejected with 400 Bad Request (OPEN -> CLOSED)")
    void testInvalidStatusTransitionRejected() throws Exception {
        Long ticketId = createTicketAs("Direct close attempt", "Trying to close open ticket", employeeToken);

        // Direct transition OPEN -> CLOSED is illegal
        TicketStatusUpdateRequest request = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.CLOSED)
                .reason("Directly closing")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Invalid ticket status transition")));
    }

    @Test
    @DisplayName("15. Status history record is generated for every transition")
    void testStatusHistoryIsRecorded() throws Exception {
        Long ticketId = createTicketAs("Track my history", "Tracking changes", employeeToken);
        assignTicketAs(ticketId, engineerUser.getId(), managerToken);
        updateStatusAs(ticketId, TicketStatus.IN_PROGRESS, "Started diagnosis", engineerToken);

        List<TicketStatusHistory> histories = ticketStatusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId);
        assertThat(histories).hasSize(3); // Initial creation (null -> OPEN), Assignment (OPEN -> ASSIGNED), Transition (ASSIGNED -> IN_PROGRESS)

        assertThat(histories.get(0).getNewStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(histories.get(1).getOldStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(histories.get(1).getNewStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(histories.get(2).getOldStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(histories.get(2).getNewStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("16. Unauthorized status change fails with 403 Forbidden")
    void testUnauthorizedStatusChangeFails() throws Exception {
        Long ticketId = createTicketAs("Cannot resolve own ticket", "Employee cannot resolve", employeeToken);
        assignTicketAs(ticketId, engineerUser.getId(), managerToken);

        // Employee attempts to mark ticket as RESOLVED
        TicketStatusUpdateRequest request = TicketStatusUpdateRequest.builder()
                .status(TicketStatus.RESOLVED)
                .reason("I want to resolve it myself")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Employees are only permitted")));
    }

    // =========================================================================
    // 4. ASSIGNMENT & REASSIGNMENT (Scenarios 17 - 20)
    // =========================================================================

    @Test
    @DisplayName("17. Manager can assign engineer to ticket (200 OK)")
    void testManagerAssignsEngineer() throws Exception {
        Long ticketId = createTicketAs("Assign me please", "Need engineer assignment", employeeToken);

        TicketAssignmentRequest request = TicketAssignmentRequest.builder()
                .engineerId(engineerUser.getId())
                .notes("Assigned for initial triage")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedEngineer.email").value("engineer@techconnect.com"));

        List<TicketAssignment> assignments = ticketAssignmentRepository.findByTicketIdOrderByAssignedAtDesc(ticketId);
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getAssignedEngineer().getId()).isEqualTo(engineerUser.getId());
        assertThat(assignments.get(0).getAssignedBy().getId()).isEqualTo(managerUser.getId());
    }

    @Test
    @DisplayName("18. Admin can assign engineer to ticket (200 OK)")
    void testAdminAssignsEngineer() throws Exception {
        Long ticketId = createTicketAs("Admin assign ticket", "Need engineer assignment", employeeToken);

        TicketAssignmentRequest request = TicketAssignmentRequest.builder()
                .engineerId(engineer2User.getId())
                .notes("Assigned by admin")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedEngineer.email").value("engineer2@techconnect.com"));
    }

    @Test
    @DisplayName("19. Invalid engineer assignment (assigning non-engineer) fails with 400 Bad Request")
    void testInvalidEngineerAssignmentFails() throws Exception {
        Long ticketId = createTicketAs("Assigning invalid user", "Trying to assign employee as engineer", employeeToken);

        // Attempting to assign employeeUser (ROLE_EMPLOYEE) as engineer
        TicketAssignmentRequest request = TicketAssignmentRequest.builder()
                .engineerId(employeeUser.getId())
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("without an ENGINEER or ADMIN role")));
    }

    @Test
    @DisplayName("20. Employee cannot assign engineer (403 Forbidden)")
    void testEmployeeCannotAssignEngineer() throws Exception {
        Long ticketId = createTicketAs("Unauthorized assign attempt", "Employee trying to assign", employeeToken);

        TicketAssignmentRequest request = TicketAssignmentRequest.builder()
                .engineerId(engineerUser.getId())
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Employees are not authorized to assign")));
    }

    // =========================================================================
    // 5. COMMENTS (Scenarios 21 - 23)
    // =========================================================================

    @Test
    @DisplayName("21. Authorized user can add comment to ticket (201 Created)")
    void testAuthorizedUserCanComment() throws Exception {
        Long ticketId = createTicketAs("Need comment thread", "Initial problem", employeeToken);

        TicketCommentRequest request = TicketCommentRequest.builder()
                .content("I have verified the network cable is securely plugged in.")
                .isInternal(false)
                .build();

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.content").value("I have verified the network cable is securely plugged in."))
                .andExpect(jsonPath("$.authorEmail").value("employee@techconnect.com"));
    }

    @Test
    @DisplayName("22. Unauthorized user cannot comment on restricted ticket (403 Forbidden)")
    void testUnauthorizedUserCannotComment() throws Exception {
        Long ticketId = createTicketAs("Private Ticket", "Secret details", employeeToken);
        assignTicketAs(ticketId, engineerUser.getId(), adminToken);
        updateStatusAs(ticketId, TicketStatus.IN_PROGRESS, null, engineerToken);

        // Employee 2 attempts to post a comment
        TicketCommentRequest request = TicketCommentRequest.builder()
                .content("Intruder trying to comment")
                .build();

        mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + employee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("23. Comment author is derived strictly from authenticated context")
    void testCommentAuthorComesFromAuthenticatedContext() throws Exception {
        Long ticketId = createTicketAs("Testing comment author", "Checking identity", employeeToken);
        assignTicketAs(ticketId, engineerUser.getId(), adminToken);

        TicketCommentRequest request = TicketCommentRequest.builder()
                .content("Engineer review note")
                .isInternal(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseNode.get("authorEmail").asText()).isEqualTo("engineer@techconnect.com");
        assertThat(responseNode.get("authorId").asLong()).isEqualTo(engineerUser.getId());
    }

    // =========================================================================
    // 6. SECURITY & REGRESSION (Scenarios 24 - 28)
    // =========================================================================

    @Test
    @DisplayName("24. Missing JWT returns 401 Unauthorized for ticket endpoints")
    void testMissingJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("25. Insufficient ownership/role returns 403 Forbidden")
    void testInsufficientOwnershipReturns403() throws Exception {
        Long ticketId = createTicketAs("Confidential Employee 2 Ticket", "Private issue", employee2Token);

        // Employee 1 attempts update
        UpdateTicketRequest updateRequest = UpdateTicketRequest.builder()
                .title("Hacked Title")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("26. Filtering and pagination works as expected on /api/tickets/my")
    void testPaginationAndFiltering() throws Exception {
        createTicketWith("Network Issue 1", TicketCategory.NETWORK, Priority.HIGH, employeeToken);
        createTicketWith("Hardware Issue 2", TicketCategory.HARDWARE, Priority.LOW, employeeToken);
        createTicketWith("Network Issue 3", TicketCategory.NETWORK, Priority.CRITICAL, employeeToken);

        // Filter by category=NETWORK
        mockMvc.perform(get("/api/tickets/my?category=NETWORK&page=0&size=10")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].category").value("NETWORK"))
                .andExpect(jsonPath("$.content[1].category").value("NETWORK"));

        // Filter by priority=CRITICAL
        mockMvc.perform(get("/api/tickets/my?priority=CRITICAL")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Network Issue 3"));
    }

    // =========================================================================
    // HELPER TEST METHODS
    // =========================================================================

    private Long createTicketAs(String title, String desc, String token) throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title(title)
                .description(desc)
                .category(TicketCategory.SOFTWARE)
                .priority(Priority.MEDIUM)
                .build();

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private void createTicketWith(String title, TicketCategory cat, Priority prio, String token) throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .title(title)
                .description("Detailed description for " + title)
                .category(cat)
                .priority(prio)
                .build();

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void assignTicketAs(Long ticketId, Long engineerId, String token) throws Exception {
        TicketAssignmentRequest request = TicketAssignmentRequest.builder()
                .engineerId(engineerId)
                .notes("Assigned via test helper")
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/assignment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void updateStatusAs(Long ticketId, TicketStatus status, String reason, String token) throws Exception {
        TicketStatusUpdateRequest request = TicketStatusUpdateRequest.builder()
                .status(status)
                .reason(reason != null ? reason : "Status change")
                .resolutionDescription(status == TicketStatus.RESOLVED ? "Issue resolved successfully" : null)
                .build();

        mockMvc.perform(patch("/api/tickets/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
