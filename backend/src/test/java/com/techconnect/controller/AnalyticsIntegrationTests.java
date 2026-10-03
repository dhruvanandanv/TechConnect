package com.techconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.entity.Role;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.TicketRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String managerToken;
    private User manager;
    private User engineer;

    @BeforeEach
    void setUp() {
        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_MANAGER).description("Manager").build()));

        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_ENGINEER).description("Engineer").build()));

        manager = userRepository.findByEmail("analytics_mgr@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("analytics_mgr@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .firstName("Analytics")
                        .lastName("Manager")
                        .role(managerRole)
                        .isActive(true)
                        .build()));

        engineer = userRepository.findByEmail("analytics_eng@techconnect.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("analytics_eng@techconnect.com")
                        .password(passwordEncoder.encode("SecurePass123!"))
                        .firstName("Analytics")
                        .lastName("Engineer")
                        .role(engineerRole)
                        .isActive(true)
                        .build()));

        managerToken = jwtService.generateToken(
                manager.getEmail(),
                java.util.Collections.singletonList(managerRole.getName().name())
        );

        // Seed sample tickets for analytics
        ticketRepository.save(Ticket.builder()
                .title("Network Outage in Data Center")
                .description("Switch failure in rack B")
                .category(TicketCategory.NETWORK)
                .priority(Priority.CRITICAL)
                .status(TicketStatus.OPEN)
                .createdBy(manager)
                .assignedEngineer(engineer)
                .slaDeadline(LocalDateTime.now().plusHours(2))
                .build());

        ticketRepository.save(Ticket.builder()
                .title("Keyboard replacement")
                .description("Space bar stuck")
                .category(TicketCategory.HARDWARE)
                .priority(Priority.LOW)
                .status(TicketStatus.RESOLVED)
                .createdBy(manager)
                .assignedEngineer(engineer)
                .resolvedAt(LocalDateTime.now().minusMinutes(30))
                .resolutionDescription("Replaced with new mechanical keyboard.")
                .slaDeadline(LocalDateTime.now().plusHours(24))
                .build());
    }

    @Test
    @DisplayName("1. Anonymous request to /api/analytics/overview should return 401 Unauthorized")
    void testAnonymousAccessReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Authenticated request to /api/analytics/overview returns 200 with all 12 metrics")
    void testAuthenticatedOverviewReturnsComprehensiveMetrics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.openTickets", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.resolvedTickets", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.priorityDistribution.CRITICAL", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.categoryDistribution.NETWORK", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.categoryDistribution.HARDWARE", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.statusDistribution.OPEN", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.averageResolutionTimeHours", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.slaCompliancePercentage", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.engineerWorkloads", not(empty())))
                .andExpect(jsonPath("$.ticketTrends", not(empty())));
    }
}
