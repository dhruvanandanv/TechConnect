package com.techconnect.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techconnect.document.KnowledgeArticle;
import com.techconnect.dto.knowledge.CreateKnowledgeArticleRequest;
import com.techconnect.dto.knowledge.KnowledgeArticleFeedbackRequest;
import com.techconnect.dto.knowledge.UpdateKnowledgeArticleRequest;
import com.techconnect.entity.Role;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.repository.RoleRepository;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleHistoryRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeArticleControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private KnowledgeArticleRepository articleRepository;

    @Autowired
    private KnowledgeArticleHistoryRepository historyRepository;

    @Value("${app.seed.dev-password:TestPassOnlyInTests!2026}")
    private String devPassword;

    private String employeeToken;
    private String engineerToken;
    private String engineer2Token;
    private String managerToken;
    private String adminToken;

    private User employeeUser;
    private User engineerUser;
    private User engineer2User;
    private User managerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
        historyRepository.deleteAll();

        Role employeeRole = roleRepository.findByName(RoleName.ROLE_EMPLOYEE).orElseThrow();
        Role engineerRole = roleRepository.findByName(RoleName.ROLE_ENGINEER).orElseThrow();
        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        employeeUser = userRepository.findByEmail("employee@techconnect.com").orElseThrow();
        engineerUser = userRepository.findByEmail("engineer@techconnect.com").orElseThrow();
        managerUser = userRepository.findByEmail("manager@techconnect.com").orElseThrow();
        adminUser = userRepository.findByEmail("admin@techconnect.com").orElseThrow();

        engineer2User = userRepository.findByEmail("engineer2@techconnect.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("engineer2@techconnect.com")
                        .password(passwordEncoder.encode(devPassword))
                        .firstName("Charlie")
                        .lastName("Engineer")
                        .role(engineerRole)
                        .isActive(true)
                        .build()));

        employeeToken = jwtService.generateToken(employeeUser.getEmail(), List.of("ROLE_EMPLOYEE"));
        engineerToken = jwtService.generateToken(engineerUser.getEmail(), List.of("ROLE_ENGINEER"));
        engineer2Token = jwtService.generateToken(engineer2User.getEmail(), List.of("ROLE_ENGINEER"));
        managerToken = jwtService.generateToken(managerUser.getEmail(), List.of("ROLE_MANAGER"));
        adminToken = jwtService.generateToken(adminUser.getEmail(), List.of("ROLE_ADMIN"));
    }

    // =========================================================================
    // 1. ARTICLE CREATION & VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Engineer creates draft article successfully")
    void testEngineerCreatesArticle() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Configuring Corporate VPN on macOS")
                .summary("Step-by-step guide to connect to corporate network via Cisco AnyConnect")
                .category(TicketCategory.VPN)
                .tags(List.of("vpn", "macos", "cisco"))
                .problem("Users unable to access internal subnets from remote devices")
                .cause("DNS split tunneling routing misconfiguration")
                .resolution("Install profile v3.2, flush local DNS cache, and reconnect")
                .build();

        mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Configuring Corporate VPN on macOS"))
                .andExpect(jsonPath("$.slug").value("configuring-corporate-vpn-on-macos"))
                .andExpect(jsonPath("$.category").value("VPN"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.authorName").value("Alex Engineer"))
                .andExpect(jsonPath("$.viewCount").value(0));
    }

    @Test
    @DisplayName("Employee cannot create article - 403 Forbidden")
    void testEmployeeCannotCreateArticle() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Unauthorized Employee Article")
                .summary("Summary")
                .category(TicketCategory.SOFTWARE)
                .problem("Problem")
                .resolution("Resolution")
                .build();

        mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Employees are not authorized to create")));
    }

    @Test
    @DisplayName("Validation fails when required fields are blank - 400 Bad Request")
    void testCreateArticleValidationFailure() throws Exception {
        CreateKnowledgeArticleRequest invalid = CreateKnowledgeArticleRequest.builder()
                .title("") // Blank
                .summary("") // Blank
                .category(null) // Null
                .problem("") // Blank
                .resolution("") // Blank
                .build();

        mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    // =========================================================================
    // 2. READ PERMISSIONS & DRAFT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Employee cannot view draft article - 403 Forbidden")
    void testEmployeeCannotViewDraftArticle() throws Exception {
        // Create draft article by engineer
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Confidential Draft Documentation")
                .summary("Draft for internal engineer review")
                .category(TicketCategory.SECURITY)
                .problem("Internal security breach procedure")
                .resolution("Follow protocol SEC-09")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String articleId = json.get("id").asText();

        // Employee attempts to read draft
        mockMvc.perform(get("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("unpublished")));
    }

    @Test
    @DisplayName("Engineer cannot view another engineer's draft - 403 Forbidden")
    void testEngineerCannotViewOtherEngineerDraft() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Engineer 1 Private Draft")
                .summary("Draft by Engineer 1")
                .category(TicketCategory.SOFTWARE)
                .problem("Private bug")
                .resolution("Private fix")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Engineer 2 attempts to read Engineer 1's draft
        mockMvc.perform(get("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + engineer2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Manager can view any draft article")
    void testManagerCanViewAnyDraft() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Engineer 1 Draft Under Review")
                .summary("Draft for manager approval")
                .category(TicketCategory.ACCESS_MANAGEMENT)
                .problem("SSO lockout")
                .resolution("Reset Azure AD token")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Manager reads draft
        mockMvc.perform(get("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Engineer 1 Draft Under Review"));
    }

    // =========================================================================
    // 3. LIFECYCLE STATE TRANSITIONS (PUBLISH, ARCHIVE, DRAFT)
    // =========================================================================

    @Test
    @DisplayName("Complete article lifecycle: Draft -> Publish -> Archive -> Draft")
    void testArticleLifecycleTransitions() throws Exception {
        // 1. Create Draft
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Office 365 License Allocation Procedure")
                .summary("Process for provisioning E5 licenses")
                .category(TicketCategory.SOFTWARE)
                .tags(List.of("o365", "license", "cloud"))
                .problem("New joiners missing Office 365 apps")
                .resolution("Assign via Admin portal -> Licenses -> Enterprise E5")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // 2. Publish
        mockMvc.perform(patch("/api/knowledge/articles/" + articleId + "/publish")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // 3. Verify Employee can now view published article
        mockMvc.perform(get("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));

        // 4. Archive
        mockMvc.perform(patch("/api/knowledge/articles/" + articleId + "/archive")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        // 5. Employee can no longer view archived article
        mockMvc.perform(get("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // 6. Revert/Restore to Draft
        mockMvc.perform(patch("/api/knowledge/articles/" + articleId + "/draft")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // 7. Verify history contains all transitions
        mockMvc.perform(get("/api/knowledge/articles/" + articleId + "/history")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4))); // CREATED, PUBLISHED, ARCHIVED, RESTORED
    }

    @Test
    @DisplayName("Invalid state transition throws 400 Bad Request")
    void testInvalidTransitionThrowsBadRequest() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("State Transition Test Article")
                .summary("Summary")
                .category(TicketCategory.NETWORK)
                .problem("Problem")
                .resolution("Resolution")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Attempting to revert a DRAFT into a DRAFT should fail
        mockMvc.perform(patch("/api/knowledge/articles/" + articleId + "/draft")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already in DRAFT")));
    }

    // =========================================================================
    // 4. ARTICLE UPDATES & VERSIONING
    // =========================================================================

    @Test
    @DisplayName("Updating published article increments version from 1 to 2")
    void testUpdatingPublishedArticleIncrementsVersion() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Wi-Fi 6 Setup Guide")
                .summary("Connecting to 802.11ax corporate APs")
                .category(TicketCategory.NETWORK)
                .problem("Cannot connect to TechConnect-Secure SSID")
                .resolution("Install CA Certificate v1")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Publish article
        mockMvc.perform(patch("/api/knowledge/articles/" + articleId + "/publish")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // Update resolution on published article
        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .resolution("Install CA Certificate v2 (SHA-384)")
                .build();

        mockMvc.perform(put("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.resolution").value("Install CA Certificate v2 (SHA-384)"));
    }

    @Test
    @DisplayName("Engineer cannot update another engineer's article - 403 Forbidden")
    void testEngineerCannotUpdateOtherEngineerArticle() throws Exception {
        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Engineer 1 Protected Guide")
                .summary("Guide authored by Dave Engineer")
                .category(TicketCategory.SECURITY)
                .problem("Phishing reporting")
                .resolution("Click PhishAlarm button")
                .build();

        MvcResult result = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String articleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .summary("Attempted tampering by Engineer 2")
                .build();

        mockMvc.perform(put("/api/knowledge/articles/" + articleId)
                        .header("Authorization", "Bearer " + engineer2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Engineers can only edit their own")));
    }

    // =========================================================================
    // 5. SEARCH & FILTERING
    // =========================================================================

    @Test
    @DisplayName("Keyword search returns published matching articles")
    void testKeywordSearch() throws Exception {
        // Create and publish VPN article
        CreateKnowledgeArticleRequest art1 = CreateKnowledgeArticleRequest.builder()
                .title("GlobalProtect VPN Installation")
                .summary("Guide for GlobalProtect client setup")
                .category(TicketCategory.VPN)
                .tags(List.of("vpn", "paloalto"))
                .problem("Client error 504 gateway timeout")
                .resolution("Switch gateway to vpn-secondary.techconnect.com")
                .build();

        MvcResult res1 = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(art1)))
                .andExpect(status().isCreated())
                .andReturn();
        String id1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/knowledge/articles/" + id1 + "/publish")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // Perform keyword search as employee
        mockMvc.perform(get("/api/knowledge/search?q=GlobalProtect")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.searchType").value("KEYWORD"))
                .andExpect(jsonPath("$.articles[0].title").value("GlobalProtect VPN Installation"));
    }

    @Test
    @DisplayName("Category filtering filters published articles correctly")
    void testCategoryFilter() throws Exception {
        // Create and publish Hardware article
        CreateKnowledgeArticleRequest hw = CreateKnowledgeArticleRequest.builder()
                .title("Docking Station Firmware Flash")
                .summary("Fix dual display flickering on Dell docks")
                .category(TicketCategory.HARDWARE)
                .problem("Monitors blink intermittently")
                .resolution("Flash MST hub firmware to v1.21")
                .build();

        MvcResult res = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hw)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/knowledge/articles/" + id + "/publish")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // Employee fetches only HARDWARE articles
        mockMvc.perform(get("/api/knowledge/articles?category=HARDWARE")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].category").value("HARDWARE"));

        // Fetching SOFTWARE category returns empty list
        mockMvc.perform(get("/api/knowledge/articles?category=SOFTWARE")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // =========================================================================
    // 6. FEEDBACK & VIEW COUNT
    // =========================================================================

    @Test
    @DisplayName("Helpful and not helpful feedback votes work and prevent duplicate voting")
    void testFeedbackVoting() throws Exception {
        CreateKnowledgeArticleRequest art = CreateKnowledgeArticleRequest.builder()
                .title("Password Reset Self-Service")
                .summary("Use self-service portal to reset Active Directory password")
                .category(TicketCategory.ACCESS_MANAGEMENT)
                .problem("Locked out of Windows account")
                .resolution("Navigate to https://sspr.techconnect.com and verify MFA")
                .build();

        MvcResult res = mockMvc.perform(post("/api/knowledge/articles")
                        .header("Authorization", "Bearer " + engineerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(art)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/knowledge/articles/" + id + "/publish")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // Employee votes helpful
        mockMvc.perform(post("/api/knowledge/articles/" + id + "/feedback")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeArticleFeedbackRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.notHelpfulCount").value(0))
                .andExpect(jsonPath("$.userHasVoted").value(true));

        // Employee votes again (duplicate vote prevention)
        mockMvc.perform(post("/api/knowledge/articles/" + id + "/feedback")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KnowledgeArticleFeedbackRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(1)); // Count remains 1
    }

    // =========================================================================
    // 7. TAXONOMY ENDPOINTS (CATEGORIES & TAGS)
    // =========================================================================

    @Test
    @DisplayName("Get categories returns all standard TicketCategory values")
    void testGetCategories() throws Exception {
        mockMvc.perform(get("/api/knowledge/categories")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("VPN")))
                .andExpect(jsonPath("$", hasItem("HARDWARE")))
                .andExpect(jsonPath("$", hasItem("SOFTWARE")))
                .andExpect(jsonPath("$", hasItem("NETWORK")))
                .andExpect(jsonPath("$", hasItem("SECURITY")));
    }
}
