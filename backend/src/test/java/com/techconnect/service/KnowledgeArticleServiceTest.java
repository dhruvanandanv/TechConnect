package com.techconnect.service;

import com.techconnect.document.KnowledgeArticle;
import com.techconnect.document.KnowledgeArticleHistory;
import com.techconnect.dto.knowledge.*;
import com.techconnect.entity.Role;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.KnowledgeArticleHistoryAction;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.exception.InvalidKnowledgeArticleStateTransitionException;
import com.techconnect.exception.KnowledgeArticleAccessDeniedException;
import com.techconnect.exception.KnowledgeArticleNotFoundException;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleHistoryRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
import com.techconnect.service.impl.KnowledgeArticleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeArticleServiceTest {

    @Mock
    private KnowledgeArticleRepository articleRepository;

    @Mock
    private KnowledgeArticleHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private KnowledgeArticleServiceImpl articleService;

    private User employeeUser;
    private User engineerUser1;
    private User engineerUser2;
    private User managerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role employeeRole = Role.builder().id(1L).name(RoleName.ROLE_EMPLOYEE).build();
        Role engineerRole = Role.builder().id(2L).name(RoleName.ROLE_ENGINEER).build();
        Role managerRole = Role.builder().id(3L).name(RoleName.ROLE_MANAGER).build();
        Role adminRole = Role.builder().id(4L).name(RoleName.ROLE_ADMIN).build();

        employeeUser = User.builder().id(101L).email("emp@techconnect.com").firstName("Emp").lastName("User").role(employeeRole).build();
        engineerUser1 = User.builder().id(201L).email("eng1@techconnect.com").firstName("Alice").lastName("Engineer").role(engineerRole).build();
        engineerUser2 = User.builder().id(202L).email("eng2@techconnect.com").firstName("Bob").lastName("Engineer").role(engineerRole).build();
        managerUser = User.builder().id(301L).email("mgr@techconnect.com").firstName("Carol").lastName("Manager").role(managerRole).build();
        adminUser = User.builder().id(401L).email("admin@techconnect.com").firstName("Dave").lastName("Admin").role(adminRole).build();
    }

    // 1. Create article by Engineer
    @Test
    @DisplayName("1. Engineer successfully creates article")
    void testEngineerCreatesArticle() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));
        when(articleRepository.existsBySlug(anyString())).thenReturn(false);

        KnowledgeArticle saved = KnowledgeArticle.builder()
                .id("art-1")
                .title("VPN Troubleshooting Guide")
                .slug("vpn-troubleshooting-guide")
                .summary("Steps to resolve VPN disconnections")
                .problem("Users disconnect frequently")
                .cause("Outdated Cisco AnyConnect profile")
                .resolution("Update profile and restart daemon")
                .category(TicketCategory.VPN)
                .tags(List.of("vpn", "cisco"))
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .authorName("Alice Engineer")
                .authorEmail(engineerUser1.getEmail())
                .version(1)
                .viewCount(0L)
                .helpfulCount(0L)
                .notHelpfulCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(articleRepository.save(any(KnowledgeArticle.class))).thenReturn(saved);

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("VPN Troubleshooting Guide")
                .summary("Steps to resolve VPN disconnections")
                .problem("Users disconnect frequently")
                .cause("Outdated Cisco AnyConnect profile")
                .resolution("Update profile and restart daemon")
                .category(TicketCategory.VPN)
                .tags(List.of("VPN", "Cisco", "vpn"))
                .build();

        KnowledgeArticleResponse response = articleService.createArticle(request, engineerUser1.getEmail());

        assertThat(response.getId()).isEqualTo("art-1");
        assertThat(response.getSlug()).isEqualTo("vpn-troubleshooting-guide");
        assertThat(response.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(response.getAuthorId()).isEqualTo(engineerUser1.getId());
        verify(historyRepository, times(1)).save(any(KnowledgeArticleHistory.class));
    }

    // 2. Employee cannot create article
    @Test
    @DisplayName("2. Employee cannot create article - access denied")
    void testEmployeeCannotCreateArticle() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Some Title")
                .summary("Some Summary")
                .problem("Problem")
                .resolution("Resolution")
                .category(TicketCategory.HARDWARE)
                .build();

        assertThatThrownBy(() -> articleService.createArticle(request, employeeUser.getEmail()))
                .isInstanceOf(KnowledgeArticleAccessDeniedException.class)
                .hasMessageContaining("Employees are not authorized to create");
    }

    // 3. Duplicate slug handling generates numbered suffix
    @Test
    @DisplayName("3. Duplicate slug generation appends unique suffix")
    void testDuplicateSlugHandling() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));
        when(articleRepository.existsBySlug("vpn-troubleshooting")).thenReturn(true);
        when(articleRepository.existsBySlug("vpn-troubleshooting-1")).thenReturn(false);

        KnowledgeArticle saved = KnowledgeArticle.builder()
                .id("art-2")
                .title("VPN Troubleshooting")
                .slug("vpn-troubleshooting-1")
                .summary("Summary")
                .problem("Problem")
                .resolution("Resolution")
                .category(TicketCategory.VPN)
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .authorName("Alice Engineer")
                .version(1)
                .build();

        when(articleRepository.save(any(KnowledgeArticle.class))).thenReturn(saved);

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("VPN Troubleshooting")
                .summary("Summary")
                .problem("Problem")
                .resolution("Resolution")
                .category(TicketCategory.VPN)
                .build();

        KnowledgeArticleResponse response = articleService.createArticle(request, engineerUser1.getEmail());
        assertThat(response.getSlug()).isEqualTo("vpn-troubleshooting-1");
    }

    // 4. Get published article succeeds and increments view count
    @Test
    @DisplayName("4. Get published article succeeds for Employee and triggers view increment")
    void testGetPublishedArticleSuccess() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle article = KnowledgeArticle.builder()
                .id("art-pub-1")
                .title("Outlook Calendar Sync")
                .slug("outlook-calendar-sync")
                .summary("Fixing Outlook sync")
                .problem("Calendar not updating")
                .resolution("Clear Outlook cache")
                .category(TicketCategory.EMAIL)
                .status(ArticleStatus.PUBLISHED)
                .authorId(engineerUser1.getId())
                .viewCount(5L)
                .build();

        when(articleRepository.findById("art-pub-1")).thenReturn(Optional.of(article));

        KnowledgeArticleResponse response = articleService.getArticleById("art-pub-1", employeeUser.getEmail());

        assertThat(response.getTitle()).isEqualTo("Outlook Calendar Sync");
        assertThat(response.getViewCount()).isEqualTo(6L);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(KnowledgeArticle.class));
    }

    // 5. Employee cannot view draft
    @Test
    @DisplayName("5. Employee cannot view draft article")
    void testEmployeeCannotViewDraft() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle article = KnowledgeArticle.builder()
                .id("art-draft-1")
                .title("Internal Draft")
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-draft-1")).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> articleService.getArticleById("art-draft-1", employeeUser.getEmail()))
                .isInstanceOf(KnowledgeArticleAccessDeniedException.class)
                .hasMessageContaining("Employees cannot view unpublished articles");
    }

    // 6. Engineer edits own draft
    @Test
    @DisplayName("6. Engineer can edit own draft")
    void testEngineerEditsOwnDraft() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle existing = KnowledgeArticle.builder()
                .id("art-draft-1")
                .title("Old Title")
                .slug("old-title")
                .summary("Old Summary")
                .problem("Old Problem")
                .resolution("Old Resolution")
                .category(TicketCategory.SOFTWARE)
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .version(1)
                .build();

        when(articleRepository.findById("art-draft-1")).thenReturn(Optional.of(existing));
        when(articleRepository.existsBySlugAndIdNot(anyString(), anyString())).thenReturn(false);
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .title("Updated Title")
                .summary("Updated Summary")
                .resolution("Updated Resolution")
                .build();

        KnowledgeArticleResponse response = articleService.updateArticle("art-draft-1", updateReq, engineerUser1.getEmail());

        assertThat(response.getTitle()).isEqualTo("Updated Title");
        assertThat(response.getSummary()).isEqualTo("Updated Summary");
        assertThat(response.getVersion()).isEqualTo(1); // Draft version remains 1
        verify(historyRepository, times(1)).save(any(KnowledgeArticleHistory.class));
    }

    // 7. Engineer cannot edit another engineer's article
    @Test
    @DisplayName("7. Engineer cannot edit another engineer's article")
    void testEngineerCannotEditOtherEngineerArticle() {
        when(userRepository.findByEmail(engineerUser2.getEmail())).thenReturn(Optional.of(engineerUser2));

        KnowledgeArticle existing = KnowledgeArticle.builder()
                .id("art-draft-1")
                .title("Alice's Article")
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId()) // Owned by Alice
                .build();

        when(articleRepository.findById("art-draft-1")).thenReturn(Optional.of(existing));

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .title("Hacked Title")
                .build();

        assertThatThrownBy(() -> articleService.updateArticle("art-draft-1", updateReq, engineerUser2.getEmail()))
                .isInstanceOf(KnowledgeArticleAccessDeniedException.class)
                .hasMessageContaining("Engineers can only edit their own knowledge articles");
    }

    // 8. Manager can manage articles
    @Test
    @DisplayName("8. Manager can edit and manage team articles")
    void testManagerCanEditArticle() {
        when(userRepository.findByEmail(managerUser.getEmail())).thenReturn(Optional.of(managerUser));

        KnowledgeArticle existing = KnowledgeArticle.builder()
                .id("art-draft-1")
                .title("Team Article")
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-draft-1")).thenReturn(Optional.of(existing));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .summary("Manager approved summary")
                .build();

        KnowledgeArticleResponse response = articleService.updateArticle("art-draft-1", updateReq, managerUser.getEmail());
        assertThat(response.getSummary()).isEqualTo("Manager approved summary");
    }

    // 9. Admin unrestricted
    @Test
    @DisplayName("9. Admin has unrestricted access to edit any article")
    void testAdminUnrestricted() {
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));

        KnowledgeArticle existing = KnowledgeArticle.builder()
                .id("art-draft-1")
                .title("Any Article")
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-draft-1")).thenReturn(Optional.of(existing));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .summary("Admin override summary")
                .build();

        KnowledgeArticleResponse response = articleService.updateArticle("art-draft-1", updateReq, adminUser.getEmail());
        assertThat(response.getSummary()).isEqualTo("Admin override summary");
    }

    // 10. Publish transition
    @Test
    @DisplayName("10. Publish transition from DRAFT to PUBLISHED")
    void testPublishTransition() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle draft = KnowledgeArticle.builder()
                .id("art-1")
                .title("Ready Article")
                .status(ArticleStatus.DRAFT)
                .authorId(engineerUser1.getId())
                .version(1)
                .build();

        when(articleRepository.findById("art-1")).thenReturn(Optional.of(draft));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        KnowledgeArticleResponse response = articleService.publishArticle("art-1", engineerUser1.getEmail());

        assertThat(response.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.getPublishedAt()).isNotNull();
        verify(historyRepository).save(argThat(h -> h.getAction() == KnowledgeArticleHistoryAction.PUBLISHED));
    }

    // 11. Invalid state transition: already PUBLISHED
    @Test
    @DisplayName("11. Invalid state transition throws exception when already PUBLISHED")
    void testInvalidPublishTransition() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle published = KnowledgeArticle.builder()
                .id("art-1")
                .status(ArticleStatus.PUBLISHED)
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-1")).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> articleService.publishArticle("art-1", engineerUser1.getEmail()))
                .isInstanceOf(InvalidKnowledgeArticleStateTransitionException.class)
                .hasMessageContaining("already PUBLISHED");
    }

    // 12. Archive transition
    @Test
    @DisplayName("12. Archive transition sets ARCHIVED status and timestamp")
    void testArchiveTransition() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle published = KnowledgeArticle.builder()
                .id("art-1")
                .status(ArticleStatus.PUBLISHED)
                .authorId(engineerUser1.getId())
                .version(1)
                .build();

        when(articleRepository.findById("art-1")).thenReturn(Optional.of(published));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        KnowledgeArticleResponse response = articleService.archiveArticle("art-1", engineerUser1.getEmail());

        assertThat(response.getStatus()).isEqualTo(ArticleStatus.ARCHIVED);
        assertThat(response.getArchivedAt()).isNotNull();
        verify(historyRepository).save(argThat(h -> h.getAction() == KnowledgeArticleHistoryAction.ARCHIVED));
    }

    // 13. Revert to draft / restore transition
    @Test
    @DisplayName("13. Revert to draft restores archived article")
    void testRevertToDraftTransition() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle archived = KnowledgeArticle.builder()
                .id("art-1")
                .status(ArticleStatus.ARCHIVED)
                .authorId(engineerUser1.getId())
                .version(1)
                .build();

        when(articleRepository.findById("art-1")).thenReturn(Optional.of(archived));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        KnowledgeArticleResponse response = articleService.revertToDraft("art-1", engineerUser1.getEmail());

        assertThat(response.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        verify(historyRepository).save(argThat(h -> h.getAction() == KnowledgeArticleHistoryAction.RESTORED));
    }

    // 14. Version increment when modifying published article
    @Test
    @DisplayName("14. Version increments from 1 to 2 when editing published content")
    void testVersionIncrementOnPublishedArticle() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle published = KnowledgeArticle.builder()
                .id("art-1")
                .title("Initial Title")
                .status(ArticleStatus.PUBLISHED)
                .authorId(engineerUser1.getId())
                .version(1)
                .problem("Initial Problem")
                .resolution("Initial Resolution")
                .build();

        when(articleRepository.findById("art-1")).thenReturn(Optional.of(published));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        UpdateKnowledgeArticleRequest updateReq = UpdateKnowledgeArticleRequest.builder()
                .resolution("Brand new enhanced resolution steps")
                .build();

        KnowledgeArticleResponse response = articleService.updateArticle("art-1", updateReq, engineerUser1.getEmail());

        assertThat(response.getVersion()).isEqualTo(2);
    }

    // 15. Keyword search restricts employees to published articles
    @Test
    @DisplayName("15. Keyword search restricts employee queries to published articles")
    void testKeywordSearchEmployeeRestricted() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle matching = KnowledgeArticle.builder()
                .id("art-1")
                .title("VPN Setup for Windows")
                .summary("VPN config")
                .category(TicketCategory.VPN)
                .status(ArticleStatus.PUBLISHED)
                .viewCount(10L)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(List.of(matching));

        KnowledgeArticleSearchResponse response = articleService.searchArticles("vpn", PageRequest.of(0, 10), employeeUser.getEmail());

        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getArticles()).hasSize(1);
        assertThat(response.getSearchType()).isEqualTo("KEYWORD");
    }

    // 16. Category filter
    @Test
    @DisplayName("16. Filter articles by category")
    void testFilterByCategory() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle netArticle = KnowledgeArticle.builder()
                .id("net-1")
                .title("Wi-Fi Configuration")
                .category(TicketCategory.NETWORK)
                .status(ArticleStatus.PUBLISHED)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(List.of(netArticle));

        Page<KnowledgeArticleSummaryResponse> page = articleService.getArticles(
                TicketCategory.NETWORK, null, null, PageRequest.of(0, 10), employeeUser.getEmail());

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getCategory()).isEqualTo(TicketCategory.NETWORK);
    }

    // 17. Tag filter
    @Test
    @DisplayName("17. Filter articles by tag")
    void testFilterByTag() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle taggedArticle = KnowledgeArticle.builder()
                .id("tag-1")
                .title("Password Reset")
                .tags(List.of("password", "security"))
                .status(ArticleStatus.PUBLISHED)
                .build();

        when(mongoTemplate.count(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(KnowledgeArticle.class))).thenReturn(List.of(taggedArticle));

        Page<KnowledgeArticleSummaryResponse> page = articleService.getArticles(
                null, null, "password", PageRequest.of(0, 10), employeeUser.getEmail());

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getTags()).contains("password");
    }

    // 18. Helpful feedback atomic update
    @Test
    @DisplayName("18. Helpful feedback increments count and prevents duplicate votes")
    void testHelpfulFeedback() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle published = KnowledgeArticle.builder()
                .id("art-fb-1")
                .status(ArticleStatus.PUBLISHED)
                .helpfulCount(1L)
                .notHelpfulCount(0L)
                .feedbackUserIds(new HashSet<>())
                .build();

        KnowledgeArticle afterVote = KnowledgeArticle.builder()
                .id("art-fb-1")
                .status(ArticleStatus.PUBLISHED)
                .helpfulCount(2L)
                .notHelpfulCount(0L)
                .feedbackUserIds(Set.of(employeeUser.getId()))
                .build();

        when(articleRepository.findById("art-fb-1")).thenReturn(Optional.of(published), Optional.of(afterVote));

        KnowledgeArticleResponse response = articleService.submitFeedback(
                "art-fb-1", new KnowledgeArticleFeedbackRequest(true), employeeUser.getEmail());

        assertThat(response.getHelpfulCount()).isEqualTo(2L);
        assertThat(response.getUserHasVoted()).isTrue();
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(KnowledgeArticle.class));
    }

    // 19. Duplicate feedback submission is idempotent
    @Test
    @DisplayName("19. Duplicate feedback by same user returns without duplicate increment")
    void testDuplicateFeedbackIdempotent() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle alreadyVoted = KnowledgeArticle.builder()
                .id("art-fb-1")
                .status(ArticleStatus.PUBLISHED)
                .helpfulCount(2L)
                .notHelpfulCount(0L)
                .feedbackUserIds(Set.of(employeeUser.getId()))
                .build();

        when(articleRepository.findById("art-fb-1")).thenReturn(Optional.of(alreadyVoted));

        KnowledgeArticleResponse response = articleService.submitFeedback(
                "art-fb-1", new KnowledgeArticleFeedbackRequest(true), employeeUser.getEmail());

        assertThat(response.getHelpfulCount()).isEqualTo(2L);
        assertThat(response.getUserHasVoted()).isTrue();
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(KnowledgeArticle.class));
    }

    // 20. Article history retrieval for author engineer
    @Test
    @DisplayName("20. Article history retrieval succeeds for author engineer")
    void testGetArticleHistoryAuthorSuccess() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));

        KnowledgeArticle article = KnowledgeArticle.builder()
                .id("art-hist-1")
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-hist-1")).thenReturn(Optional.of(article));

        KnowledgeArticleHistory h1 = KnowledgeArticleHistory.builder()
                .id("h-1")
                .articleId("art-hist-1")
                .action(KnowledgeArticleHistoryAction.CREATED)
                .performedByName("Alice Engineer")
                .version(1)
                .performedAt(LocalDateTime.now())
                .build();

        when(historyRepository.findByArticleIdOrderByPerformedAtDesc("art-hist-1")).thenReturn(List.of(h1));

        List<KnowledgeArticleHistoryResponse> history = articleService.getArticleHistory("art-hist-1", engineerUser1.getEmail());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo(KnowledgeArticleHistoryAction.CREATED);
    }

    // 21. Employee cannot view history
    @Test
    @DisplayName("21. Employee cannot view article history")
    void testEmployeeCannotViewHistory() {
        when(userRepository.findByEmail(employeeUser.getEmail())).thenReturn(Optional.of(employeeUser));

        KnowledgeArticle article = KnowledgeArticle.builder()
                .id("art-hist-1")
                .authorId(engineerUser1.getId())
                .build();

        when(articleRepository.findById("art-hist-1")).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> articleService.getArticleHistory("art-hist-1", employeeUser.getEmail()))
                .isInstanceOf(KnowledgeArticleAccessDeniedException.class)
                .hasMessageContaining("Employees cannot view article history");
    }

    // 22. Not found article throws KnowledgeArticleNotFoundException
    @Test
    @DisplayName("22. Article not found throws KnowledgeArticleNotFoundException")
    void testArticleNotFound() {
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(articleRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getArticleById("non-existent", adminUser.getEmail()))
                .isInstanceOf(KnowledgeArticleNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // 23. Tag normalization
    @Test
    @DisplayName("23. Tag normalization removes duplicates, trims, and converts to lowercase")
    void testTagNormalization() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));
        when(articleRepository.existsBySlug(anyString())).thenReturn(false);

        ArgumentCaptor<KnowledgeArticle> captor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        when(articleRepository.save(captor.capture())).thenAnswer(i -> {
            KnowledgeArticle a = i.getArgument(0);
            a.setId("art-norm");
            return a;
        });

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Tag Normalization Test")
                .summary("Summary")
                .problem("Problem")
                .resolution("Resolution")
                .category(TicketCategory.SECURITY)
                .tags(Arrays.asList("  Security ", "SECURITY", " MFA ", "", null, "mfa"))
                .build();

        articleService.createArticle(request, engineerUser1.getEmail());

        KnowledgeArticle captured = captor.getValue();
        assertThat(captured.getTags()).containsExactly("security", "mfa");
    }

    // 24. Optional ticket linkage (sourceTicketId)
    @Test
    @DisplayName("24. Article successfully links to source resolved ticket")
    void testTicketLinkage() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));
        when(articleRepository.existsBySlug(anyString())).thenReturn(false);

        ArgumentCaptor<KnowledgeArticle> captor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        when(articleRepository.save(captor.capture())).thenAnswer(i -> {
            KnowledgeArticle a = i.getArgument(0);
            a.setId("art-ticket");
            return a;
        });

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Resolution for Ticket 42")
                .summary("How we resolved ticket 42")
                .problem("Printer spooler crash")
                .resolution("Restart spooler service")
                .category(TicketCategory.HARDWARE)
                .sourceTicketId(42L)
                .build();

        articleService.createArticle(request, engineerUser1.getEmail());

        KnowledgeArticle captured = captor.getValue();
        assertThat(captured.getSourceTicketId()).isEqualTo(42L);
        assertThat(captured.getSourceType()).isEqualTo("TICKET");
    }

    // 25. Database failure handling simulation
    @Test
    @DisplayName("25. Database failure throws appropriate DataAccessException")
    void testDatabaseFailureHandling() {
        when(userRepository.findByEmail(engineerUser1.getEmail())).thenReturn(Optional.of(engineerUser1));
        when(articleRepository.existsBySlug(anyString())).thenReturn(false);
        when(articleRepository.save(any(KnowledgeArticle.class)))
                .thenThrow(new DataAccessResourceFailureException("MongoDB connection timeout"));

        CreateKnowledgeArticleRequest request = CreateKnowledgeArticleRequest.builder()
                .title("Fail Test")
                .summary("Summary")
                .problem("Problem")
                .resolution("Resolution")
                .category(TicketCategory.OTHER)
                .build();

        assertThatThrownBy(() -> articleService.createArticle(request, engineerUser1.getEmail()))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("MongoDB connection timeout");
    }
}
