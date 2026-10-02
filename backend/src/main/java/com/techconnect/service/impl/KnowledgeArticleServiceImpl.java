package com.techconnect.service.impl;

import com.techconnect.document.KnowledgeArticle;
import com.techconnect.document.KnowledgeArticleHistory;
import com.techconnect.dto.knowledge.*;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.ArticleStatus;
import com.techconnect.entity.enums.KnowledgeArticleHistoryAction;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.exception.InvalidKnowledgeArticleStateTransitionException;
import com.techconnect.exception.KnowledgeArticleAccessDeniedException;
import com.techconnect.exception.KnowledgeArticleNotFoundException;
import com.techconnect.exception.ResourceNotFoundException;
import com.techconnect.repository.UserRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleHistoryRepository;
import com.techconnect.repository.mongodb.KnowledgeArticleRepository;
import com.techconnect.service.KnowledgeArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeArticleHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final com.techconnect.client.AiKnowledgeVectorClient vectorClient;

    @Override
    public KnowledgeArticleResponse createArticle(CreateKnowledgeArticleRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        assertCanCreate(user);

        log.info("Creating knowledge article '{}' by user '{}' (role: {})",
                request.getTitle(), userEmail, user.getRole().getName());

        LocalDateTime now = LocalDateTime.now();
        List<String> normalizedTags = normalizeTags(request.getTags());
        String slug = generateUniqueSlug(request.getTitle(), null);
        String synthesizedContent = synthesizeContent(request.getContent(), request.getProblem(), request.getCause(), request.getResolution());
        String normalizedText = buildNormalizedText(request.getTitle(), request.getSummary(), request.getProblem(), request.getCause(), request.getResolution(), normalizedTags);

        ArticleStatus initialStatus = request.getStatus() != null ? request.getStatus() : ArticleStatus.DRAFT;
        LocalDateTime publishedAt = (initialStatus == ArticleStatus.PUBLISHED) ? now : null;

        KnowledgeArticle article = KnowledgeArticle.builder()
                .title(request.getTitle().trim())
                .slug(slug)
                .summary(request.getSummary().trim())
                .problem(request.getProblem().trim())
                .cause(request.getCause() != null ? request.getCause().trim() : null)
                .resolution(request.getResolution().trim())
                .content(synthesizedContent)
                .category(request.getCategory())
                .tags(normalizedTags)
                .status(initialStatus)
                .authorId(user.getId())
                .authorName(user.getFirstName() + " " + user.getLastName())
                .authorEmail(user.getEmail())
                .version(1)
                .viewCount(0L)
                .helpfulCount(0L)
                .notHelpfulCount(0L)
                .feedbackUserIds(new HashSet<>())
                .sourceType(request.getSourceTicketId() != null ? "TICKET" : "MANUAL")
                .sourceTicketId(request.getSourceTicketId())
                .createdAt(now)
                .updatedAt(now)
                .publishedAt(publishedAt)
                .normalizedText(normalizedText)
                .embeddingStatus("PENDING")
                .build();

        KnowledgeArticle saved = articleRepository.save(article);

        recordHistory(saved.getId(), KnowledgeArticleHistoryAction.CREATED, user, 1,
                "Article created with status " + initialStatus);

        return mapToResponse(saved, user.getId());
    }

    @Override
    public KnowledgeArticleResponse updateArticle(String id, UpdateKnowledgeArticleRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanModify(user, article);

        log.info("Updating knowledge article #{} '{}' by user '{}'", id, article.getTitle(), userEmail);

        LocalDateTime now = LocalDateTime.now();
        boolean meaningfulContentChanged = false;

        if (request.getTitle() != null && !request.getTitle().trim().equalsIgnoreCase(article.getTitle())) {
            article.setTitle(request.getTitle().trim());
            article.setSlug(generateUniqueSlug(request.getTitle(), article.getId()));
            meaningfulContentChanged = true;
        }

        if (request.getSummary() != null && !request.getSummary().trim().equals(article.getSummary())) {
            article.setSummary(request.getSummary().trim());
            meaningfulContentChanged = true;
        }

        if (request.getCategory() != null) {
            article.setCategory(request.getCategory());
        }

        if (request.getTags() != null) {
            article.setTags(normalizeTags(request.getTags()));
        }

        if (request.getProblem() != null && !request.getProblem().trim().equals(article.getProblem())) {
            article.setProblem(request.getProblem().trim());
            meaningfulContentChanged = true;
        }

        if (request.getCause() != null && !request.getCause().trim().equals(article.getCause())) {
            article.setCause(request.getCause().trim());
            meaningfulContentChanged = true;
        }

        if (request.getResolution() != null && !request.getResolution().trim().equals(article.getResolution())) {
            article.setResolution(request.getResolution().trim());
            meaningfulContentChanged = true;
        }

        if (request.getContent() != null && !request.getContent().trim().equals(article.getContent())) {
            article.setContent(request.getContent().trim());
            meaningfulContentChanged = true;
        } else if (meaningfulContentChanged) {
            article.setContent(synthesizeContent(null, article.getProblem(), article.getCause(), article.getResolution()));
        }

        if (request.getSourceTicketId() != null) {
            article.setSourceTicketId(request.getSourceTicketId());
            article.setSourceType("TICKET");
        }

        // If the article is already PUBLISHED and meaningful content was modified, increment version
        if (article.getStatus() == ArticleStatus.PUBLISHED && meaningfulContentChanged) {
            article.setVersion(article.getVersion() + 1);
        }

        if (meaningfulContentChanged) {
            article.setEmbeddingStatus("PENDING");
            article.setEmbeddingError(null);
        }

        article.setUpdatedAt(now);
        article.setNormalizedText(buildNormalizedText(article.getTitle(), article.getSummary(),
                article.getProblem(), article.getCause(), article.getResolution(), article.getTags()));

        KnowledgeArticle updated = articleRepository.save(article);

        recordHistory(updated.getId(), KnowledgeArticleHistoryAction.UPDATED, user, updated.getVersion(),
                meaningfulContentChanged ? "Article content updated to version " + updated.getVersion() : "Article metadata updated");

        return mapToResponse(updated, user.getId());
    }

    @Override
    public KnowledgeArticleResponse publishArticle(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanPublishOrArchive(user, article);

        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            throw new InvalidKnowledgeArticleStateTransitionException("Article is already PUBLISHED");
        }

        LocalDateTime now = LocalDateTime.now();
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(now);
        article.setArchivedAt(null);
        article.setUpdatedAt(now);

        KnowledgeArticle saved = articleRepository.save(article);
        vectorClient.updateArticleStatus(saved.getId(), "PUBLISHED");

        recordHistory(saved.getId(), KnowledgeArticleHistoryAction.PUBLISHED, user, saved.getVersion(),
                "Article published");

        log.info("Knowledge article #{} published by user '{}'", id, userEmail);
        return mapToResponse(saved, user.getId());
    }

    @Override
    public KnowledgeArticleResponse archiveArticle(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanPublishOrArchive(user, article);

        if (article.getStatus() == ArticleStatus.ARCHIVED) {
            throw new InvalidKnowledgeArticleStateTransitionException("Article is already ARCHIVED");
        }

        LocalDateTime now = LocalDateTime.now();
        article.setStatus(ArticleStatus.ARCHIVED);
        article.setArchivedAt(now);
        article.setUpdatedAt(now);

        KnowledgeArticle saved = articleRepository.save(article);
        vectorClient.updateArticleStatus(saved.getId(), "ARCHIVED");

        recordHistory(saved.getId(), KnowledgeArticleHistoryAction.ARCHIVED, user, saved.getVersion(),
                "Article archived");

        log.info("Knowledge article #{} archived by user '{}'", id, userEmail);
        return mapToResponse(saved, user.getId());
    }

    @Override
    public KnowledgeArticleResponse revertToDraft(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanPublishOrArchive(user, article);

        if (article.getStatus() == ArticleStatus.DRAFT) {
            throw new InvalidKnowledgeArticleStateTransitionException("Article is already in DRAFT status");
        }

        ArticleStatus previousStatus = article.getStatus();
        LocalDateTime now = LocalDateTime.now();
        article.setStatus(ArticleStatus.DRAFT);
        article.setUpdatedAt(now);

        KnowledgeArticle saved = articleRepository.save(article);
        vectorClient.updateArticleStatus(saved.getId(), "DRAFT");

        KnowledgeArticleHistoryAction action = (previousStatus == ArticleStatus.ARCHIVED)
                ? KnowledgeArticleHistoryAction.RESTORED
                : KnowledgeArticleHistoryAction.REVERTED_TO_DRAFT;

        recordHistory(saved.getId(), action, user, saved.getVersion(),
                "Article status transitioned from " + previousStatus + " to DRAFT");

        log.info("Knowledge article #{} reverted to DRAFT by user '{}'", id, userEmail);
        return mapToResponse(saved, user.getId());
    }

    @Override
    public KnowledgeArticleResponse getArticleById(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanRead(user, article);

        // Atomically increment view count if article is published
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            Query query = Query.query(Criteria.where("_id").is(article.getId()).and("status").is(ArticleStatus.PUBLISHED));
            Update update = new Update().inc("view_count", 1);
            mongoTemplate.updateFirst(query, update, KnowledgeArticle.class);
            article.setViewCount(article.getViewCount() + 1);
        }

        return mapToResponse(article, user.getId());
    }

    @Override
    public KnowledgeArticleResponse getArticleBySlug(String slug, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = articleRepository.findBySlug(slug)
                .orElseThrow(() -> new KnowledgeArticleNotFoundException("Knowledge article not found with slug: " + slug));

        assertCanRead(user, article);

        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            Query query = Query.query(Criteria.where("_id").is(article.getId()).and("status").is(ArticleStatus.PUBLISHED));
            Update update = new Update().inc("view_count", 1);
            mongoTemplate.updateFirst(query, update, KnowledgeArticle.class);
            article.setViewCount(article.getViewCount() + 1);
        }

        return mapToResponse(article, user.getId());
    }

    @Override
    public Page<KnowledgeArticleSummaryResponse> getArticles(
            TicketCategory category,
            ArticleStatus status,
            String tag,
            Pageable pageable,
            String userEmail) {

        User user = getUserByEmail(userEmail);

        Query query = new Query();

        // RBAC enforcement for article listing
        if (isEmployee(user)) {
            // Employees can ONLY ever receive PUBLISHED articles
            query.addCriteria(Criteria.where("status").is(ArticleStatus.PUBLISHED));
        } else if (isEngineer(user)) {
            if (status != null) {
                if (status == ArticleStatus.PUBLISHED) {
                    query.addCriteria(Criteria.where("status").is(ArticleStatus.PUBLISHED));
                } else {
                    // Engineer viewing drafts or archives: only their own
                    query.addCriteria(Criteria.where("status").is(status).and("author_id").is(user.getId()));
                }
            } else {
                // By default show published articles or engineer's own
                Criteria isPub = Criteria.where("status").is(ArticleStatus.PUBLISHED);
                Criteria isOwn = Criteria.where("author_id").is(user.getId());
                query.addCriteria(new Criteria().orOperator(isPub, isOwn));
            }
        } else {
            // Manager or Admin
            if (status != null) {
                query.addCriteria(Criteria.where("status").is(status));
            }
        }

        if (category != null) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if (tag != null && !tag.trim().isEmpty()) {
            query.addCriteria(Criteria.where("tags").is(tag.trim().toLowerCase()));
        }

        long total = mongoTemplate.count(query, KnowledgeArticle.class);

        // Sorting & pagination
        if (pageable.getSort().isUnsorted()) {
            query.with(Sort.by(Sort.Direction.DESC, "updated_at"));
        }
        query.with(pageable);

        List<KnowledgeArticle> articles = mongoTemplate.find(query, KnowledgeArticle.class);
        List<KnowledgeArticleSummaryResponse> summaries = articles.stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(summaries, pageable, total);
    }

    @Override
    public KnowledgeArticleSearchResponse searchArticles(String queryStr, Pageable pageable, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (queryStr == null || queryStr.trim().isEmpty()) {
            Page<KnowledgeArticleSummaryResponse> emptyPage = getArticles(null, ArticleStatus.PUBLISHED, null, pageable, userEmail);
            return KnowledgeArticleSearchResponse.builder()
                    .query("")
                    .totalHits(emptyPage.getTotalElements())
                    .page(emptyPage.getNumber())
                    .size(emptyPage.getSize())
                    .totalPages(emptyPage.getTotalPages())
                    .articles(emptyPage.getContent())
                    .searchType("KEYWORD")
                    .build();
        }

        String cleanedQuery = queryStr.trim();
        Pattern regexPattern = Pattern.compile(Pattern.quote(cleanedQuery), Pattern.CASE_INSENSITIVE);

        Query query = new Query();

        // Enforce RBAC on search
        if (isEmployee(user)) {
            query.addCriteria(Criteria.where("status").is(ArticleStatus.PUBLISHED));
        } else if (isEngineer(user)) {
            Criteria isPub = Criteria.where("status").is(ArticleStatus.PUBLISHED);
            Criteria isOwn = Criteria.where("author_id").is(user.getId());
            query.addCriteria(new Criteria().orOperator(isPub, isOwn));
        }

        // Multi-field keyword criteria matching on title, summary, problem, cause, resolution, tags, and category
        List<Criteria> searchFields = new ArrayList<>();
        searchFields.add(Criteria.where("title").regex(regexPattern));
        searchFields.add(Criteria.where("summary").regex(regexPattern));
        searchFields.add(Criteria.where("problem").regex(regexPattern));
        searchFields.add(Criteria.where("cause").regex(regexPattern));
        searchFields.add(Criteria.where("resolution").regex(regexPattern));
        searchFields.add(Criteria.where("tags").regex(regexPattern));

        try {
            TicketCategory categoryMatch = TicketCategory.valueOf(cleanedQuery.toUpperCase().replace(" ", "_"));
            searchFields.add(Criteria.where("category").is(categoryMatch));
        } catch (IllegalArgumentException ignored) {
            // Not a direct category match
        }

        query.addCriteria(new Criteria().orOperator(searchFields.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, KnowledgeArticle.class);

        if (pageable.getSort().isUnsorted()) {
            query.with(Sort.by(Sort.Direction.DESC, "view_count", "updated_at"));
        }
        query.with(pageable);

        List<KnowledgeArticle> articles = mongoTemplate.find(query, KnowledgeArticle.class);
        List<KnowledgeArticleSummaryResponse> summaries = articles.stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) total / (double) pageable.getPageSize());

        return KnowledgeArticleSearchResponse.builder()
                .query(cleanedQuery)
                .totalHits(total)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalPages(totalPages)
                .articles(summaries)
                .searchType("KEYWORD")
                .build();
    }

    @Override
    public KnowledgeArticleResponse submitFeedback(String id, KnowledgeArticleFeedbackRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new KnowledgeArticleAccessDeniedException("Feedback can only be submitted for published articles");
        }

        Long userId = user.getId();

        // Check if user already voted to prevent duplicate votes
        if (article.getFeedbackUserIds() != null && article.getFeedbackUserIds().contains(userId)) {
            log.info("User ID {} already submitted feedback for article #{}", userId, id);
            return mapToResponse(article, userId);
        }

        String fieldToInc = Boolean.TRUE.equals(request.getHelpful()) ? "helpful_count" : "not_helpful_count";

        Query query = Query.query(Criteria.where("_id").is(article.getId()).and("feedback_user_ids").ne(userId));
        Update update = new Update()
                .inc(fieldToInc, 1)
                .addToSet("feedback_user_ids", userId);

        mongoTemplate.updateFirst(query, update, KnowledgeArticle.class);

        // Fetch refreshed article
        KnowledgeArticle refreshed = getArticleEntity(id);
        return mapToResponse(refreshed, userId);
    }

    @Override
    public List<KnowledgeArticleHistoryResponse> getArticleHistory(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        // Employees cannot view audit history
        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees cannot view article history");
        }

        // Engineers can only view history for their own articles
        if (isEngineer(user) && !article.getAuthorId().equals(user.getId())) {
            throw new KnowledgeArticleAccessDeniedException("Engineers can only view history for their own articles");
        }

        return historyRepository.findByArticleIdOrderByPerformedAtDesc(id).stream()
                .map(this::mapToHistoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getCategories() {
        return Arrays.stream(TicketCategory.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTags() {
        Query query = Query.query(Criteria.where("status").is(ArticleStatus.PUBLISHED));
        List<String> distinctTags = mongoTemplate.findDistinct(query, "tags", KnowledgeArticle.class, String.class);
        Collections.sort(distinctTags);
        return distinctTags;
    }

    // =========================================================================
    // Phase 11 Semantic Search & Vector Ingestion
    // =========================================================================

    @Override
    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return SemanticSearchResponse.builder()
                    .searchType("SEMANTIC")
                    .query("")
                    .available(true)
                    .totalHits(0)
                    .results(new ArrayList<>())
                    .build();
        }

        List<String> allowedStatuses = null;
        List<String> allowedArticleIds = null;

        if (isEmployee(user)) {
            // Employees strictly only ever search PUBLISHED articles
            allowedStatuses = List.of(ArticleStatus.PUBLISHED.name());
        } else if (isEngineer(user)) {
            // Engineers can search published articles or their own authored articles
            allowedStatuses = List.of(ArticleStatus.PUBLISHED.name());
            List<KnowledgeArticle> ownArticles = articleRepository.findByAuthorId(user.getId());
            allowedArticleIds = ownArticles.stream().map(KnowledgeArticle::getId).collect(Collectors.toList());
        }

        SemanticSearchResponse response = vectorClient.semanticSearch(
                request.getQuery().trim(),
                request.getCategory(),
                request.getTopK(),
                request.getMinSimilarity(),
                allowedStatuses,
                allowedArticleIds
        );

        // Enrich results with article titles and slugs
        if (response.isAvailable() && response.getResults() != null) {
            for (SemanticSearchResultChunk chunk : response.getResults()) {
                if (chunk.getArticleId() != null) {
                    articleRepository.findById(chunk.getArticleId()).ifPresent(article -> {
                        chunk.setTitle(article.getTitle());
                        chunk.setSlug(article.getSlug());
                        if (chunk.getCategory() == null && article.getCategory() != null) {
                            chunk.setCategory(article.getCategory().name());
                        }
                    });
                }
            }
        }

        return response;
    }

    @Override
    public IngestionRunResponse runIngestion(String userEmail) {
        User user = getUserByEmail(userEmail);
        assertCanRunIngestion(user);

        log.info("Running knowledge vector ingestion triggered by user '{}' (role: {})", userEmail, user.getRole().getName());

        Query query = Query.query(Criteria.where("embedding_status").in("PENDING", null, "FAILED"));
        List<KnowledgeArticle> pendingArticles = mongoTemplate.find(query, KnowledgeArticle.class);

        if (pendingArticles.isEmpty()) {
            return IngestionRunResponse.builder()
                    .articlesDiscovered(0)
                    .articlesProcessed(0)
                    .chunksCreated(0)
                    .chunksEmbedded(0)
                    .failures(0)
                    .message("No pending articles requiring vector ingestion")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();

        // Safe deterministic transition to PROCESSING
        for (KnowledgeArticle article : pendingArticles) {
            article.setEmbeddingStatus("PROCESSING");
            article.setEmbeddingUpdatedAt(now);
            articleRepository.save(article);
        }

        try {
            IngestionRunResponse result = vectorClient.ingestArticlesBatch(pendingArticles);

            // On success, mark completed
            for (KnowledgeArticle article : pendingArticles) {
                article.setEmbeddingStatus("COMPLETED");
                article.setEmbeddingModel("all-MiniLM-L6-v2");
                article.setEmbeddingVersion("1.0.0");
                article.setEmbeddingUpdatedAt(LocalDateTime.now());
                article.setEmbeddingError(null);
                articleRepository.save(article);
            }

            return result;

        } catch (Exception ex) {
            log.error("Batch vector ingestion failed: {}", ex.getMessage());
            for (KnowledgeArticle article : pendingArticles) {
                article.setEmbeddingStatus("FAILED");
                article.setEmbeddingError(ex.getMessage() != null ? ex.getMessage() : "Vector ingestion failure");
                article.setEmbeddingUpdatedAt(LocalDateTime.now());
                articleRepository.save(article);
            }
            return IngestionRunResponse.builder()
                    .articlesDiscovered(pendingArticles.size())
                    .articlesProcessed(0)
                    .chunksCreated(0)
                    .chunksEmbedded(0)
                    .failures(pendingArticles.size())
                    .message("Ingestion failed: " + ex.getMessage())
                    .build();
        }
    }

    @Override
    public ArticleReindexResponse reindexArticle(String id, String userEmail) {
        User user = getUserByEmail(userEmail);
        KnowledgeArticle article = getArticleEntity(id);

        assertCanModify(user, article);

        log.info("Reindexing knowledge article #{} '{}' triggered by user '{}'", id, article.getTitle(), userEmail);

        article.setEmbeddingStatus("PENDING");
        article.setEmbeddingUpdatedAt(LocalDateTime.now());
        articleRepository.save(article);

        try {
            ArticleReindexResponse response = vectorClient.reindexArticle(article);

            article.setEmbeddingStatus("COMPLETED");
            article.setEmbeddingModel("all-MiniLM-L6-v2");
            article.setEmbeddingVersion("1.0.0");
            article.setEmbeddingUpdatedAt(LocalDateTime.now());
            article.setEmbeddingError(null);
            articleRepository.save(article);

            recordHistory(article.getId(), KnowledgeArticleHistoryAction.UPDATED, user, article.getVersion(),
                    "Article reindexed in semantic vector index");

            return response;

        } catch (Exception ex) {
            log.error("Failed to reindex article #{}: {}", id, ex.getMessage());
            article.setEmbeddingStatus("FAILED");
            article.setEmbeddingError(ex.getMessage() != null ? ex.getMessage() : "Reindex failure");
            article.setEmbeddingUpdatedAt(LocalDateTime.now());
            articleRepository.save(article);

            throw new RuntimeException("Article reindex failed: " + ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Internal Helper Methods & Validations
    // =========================================================================

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private KnowledgeArticle getArticleEntity(String id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new KnowledgeArticleNotFoundException("Knowledge article not found with id: " + id));
    }

    private void assertCanCreate(User user) {
        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees are not authorized to create knowledge articles");
        }
    }

    private void assertCanRunIngestion(User user) {
        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees are not authorized to trigger knowledge ingestion");
        }
    }

    private void assertCanModify(User user, KnowledgeArticle article) {
        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees are not authorized to edit knowledge articles");
        }
        if (isEngineer(user) && !article.getAuthorId().equals(user.getId())) {
            throw new KnowledgeArticleAccessDeniedException("Engineers can only edit their own knowledge articles");
        }
    }

    private void assertCanPublishOrArchive(User user, KnowledgeArticle article) {
        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees are not authorized to publish or archive knowledge articles");
        }
        if (isEngineer(user) && !article.getAuthorId().equals(user.getId())) {
            throw new KnowledgeArticleAccessDeniedException("Engineers can only manage their own knowledge articles");
        }
    }

    private void assertCanRead(User user, KnowledgeArticle article) {
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            return; // Published is readable by all authenticated users
        }

        if (isEmployee(user)) {
            throw new KnowledgeArticleAccessDeniedException("Employees cannot view unpublished articles");
        }

        if (isEngineer(user) && !article.getAuthorId().equals(user.getId())) {
            throw new KnowledgeArticleAccessDeniedException("Engineers cannot view drafts or archived articles authored by others");
        }
    }

    private boolean isEmployee(User user) {
        return user.getRole().getName() == RoleName.ROLE_EMPLOYEE;
    }

    private boolean isEngineer(User user) {
        return user.getRole().getName() == RoleName.ROLE_ENGINEER;
    }

    private boolean isManager(User user) {
        return user.getRole().getName() == RoleName.ROLE_MANAGER;
    }

    private boolean isAdmin(User user) {
        return user.getRole().getName() == RoleName.ROLE_ADMIN;
    }

    private String generateUniqueSlug(String title, String currentArticleId) {
        String baseSlug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (baseSlug.isEmpty()) {
            baseSlug = "kb-article";
        }

        String candidateSlug = baseSlug;
        int counter = 1;

        while (true) {
            boolean exists = (currentArticleId == null)
                    ? articleRepository.existsBySlug(candidateSlug)
                    : articleRepository.existsBySlugAndIdNot(candidateSlug, currentArticleId);

            if (!exists) {
                return candidateSlug;
            }
            candidateSlug = baseSlug + "-" + counter++;
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(t -> !t.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String synthesizeContent(String explicitContent, String problem, String cause, String resolution) {
        if (explicitContent != null && !explicitContent.trim().isEmpty()) {
            return explicitContent.trim();
        }
        StringBuilder sb = new StringBuilder();
        if (problem != null && !problem.trim().isEmpty()) {
            sb.append("### Problem & Symptoms\n\n").append(problem.trim()).append("\n\n");
        }
        if (cause != null && !cause.trim().isEmpty()) {
            sb.append("### Possible Cause\n\n").append(cause.trim()).append("\n\n");
        }
        if (resolution != null && !resolution.trim().isEmpty()) {
            sb.append("### Resolution Steps\n\n").append(resolution.trim()).append("\n");
        }
        return sb.toString();
    }

    private String buildNormalizedText(String title, String summary, String problem, String cause, String resolution, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            sb.append("Title: ").append(title.trim()).append("\n");
        }
        if (summary != null && !summary.trim().isEmpty()) {
            sb.append("Summary: ").append(summary.trim()).append("\n");
        }
        if (problem != null && !problem.trim().isEmpty()) {
            sb.append("Problem: ").append(problem.trim()).append("\n");
        }
        if (cause != null && !cause.trim().isEmpty()) {
            sb.append("Cause: ").append(cause.trim()).append("\n");
        }
        if (resolution != null && !resolution.trim().isEmpty()) {
            sb.append("Resolution: ").append(resolution.trim()).append("\n");
        }
        if (tags != null && !tags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }
        return sb.toString();
    }


    private void recordHistory(String articleId, KnowledgeArticleHistoryAction action, User user, int version, String details) {
        KnowledgeArticleHistory history = KnowledgeArticleHistory.builder()
                .articleId(articleId)
                .action(action)
                .performedById(user.getId())
                .performedByName(user.getFirstName() + " " + user.getLastName())
                .performedByEmail(user.getEmail())
                .performedAt(LocalDateTime.now())
                .version(version)
                .details(details)
                .build();
        historyRepository.save(history);
    }

    private KnowledgeArticleResponse mapToResponse(KnowledgeArticle a, Long currentUserId) {
        boolean voted = a.getFeedbackUserIds() != null && currentUserId != null && a.getFeedbackUserIds().contains(currentUserId);
        return KnowledgeArticleResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .slug(a.getSlug())
                .summary(a.getSummary())
                .problem(a.getProblem())
                .cause(a.getCause())
                .resolution(a.getResolution())
                .content(a.getContent())
                .category(a.getCategory())
                .tags(a.getTags())
                .status(a.getStatus())
                .authorId(a.getAuthorId())
                .authorName(a.getAuthorName())
                .authorEmail(a.getAuthorEmail())
                .version(a.getVersion())
                .viewCount(a.getViewCount())
                .helpfulCount(a.getHelpfulCount())
                .notHelpfulCount(a.getNotHelpfulCount())
                .userHasVoted(voted)
                .sourceType(a.getSourceType())
                .sourceTicketId(a.getSourceTicketId())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .publishedAt(a.getPublishedAt())
                .archivedAt(a.getArchivedAt())
                .embeddingStatus(a.getEmbeddingStatus())
                .embeddingModel(a.getEmbeddingModel())
                .embeddingVersion(a.getEmbeddingVersion())
                .embeddingUpdatedAt(a.getEmbeddingUpdatedAt())
                .embeddingError(a.getEmbeddingError())
                .build();
    }

    private KnowledgeArticleSummaryResponse mapToSummaryResponse(KnowledgeArticle a) {
        return KnowledgeArticleSummaryResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .slug(a.getSlug())
                .summary(a.getSummary())
                .category(a.getCategory())
                .tags(a.getTags())
                .status(a.getStatus())
                .authorId(a.getAuthorId())
                .authorName(a.getAuthorName())
                .version(a.getVersion())
                .viewCount(a.getViewCount())
                .helpfulCount(a.getHelpfulCount())
                .updatedAt(a.getUpdatedAt())
                .publishedAt(a.getPublishedAt())
                .embeddingStatus(a.getEmbeddingStatus())
                .build();
    }

    private KnowledgeArticleHistoryResponse mapToHistoryResponse(KnowledgeArticleHistory h) {
        return KnowledgeArticleHistoryResponse.builder()
                .id(h.getId())
                .articleId(h.getArticleId())
                .action(h.getAction())
                .performedById(h.getPerformedById())
                .performedByName(h.getPerformedByName())
                .performedByEmail(h.getPerformedByEmail())
                .performedAt(h.getPerformedAt())
                .version(h.getVersion())
                .details(h.getDetails())
                .build();
    }
}
