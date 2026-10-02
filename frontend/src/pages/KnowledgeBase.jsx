import React, { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import knowledgeService from '../services/knowledgeService';
import KnowledgeSearchBar from '../components/KnowledgeSearchBar';
import KnowledgeCategoryFilter from '../components/KnowledgeCategoryFilter';
import KnowledgeArticleCard from '../components/KnowledgeArticleCard';
import SemanticSearchResultCard from '../components/SemanticSearchResultCard';

const KnowledgeBase = () => {
  const { currentUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const isStaff = currentUser && ['ROLE_ENGINEER', 'ROLE_MANAGER', 'ROLE_ADMIN'].includes(currentUser.role);

  // URL state
  const queryParam = searchParams.get('q') || '';
  const searchTypeParam = searchParams.get('type') || 'SEMANTIC';
  const categoryParam = searchParams.get('category') || 'ALL';
  const statusParam = searchParams.get('status') || 'PUBLISHED';
  const tagParam = searchParams.get('tag') || '';
  const pageParam = parseInt(searchParams.get('page') || '0', 10);

  // Component state
  const [articles, setArticles] = useState([]);
  const [semanticResults, setSemanticResults] = useState([]);
  const [searchType, setSearchType] = useState(searchTypeParam);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(pageParam);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [aiUnavailable, setAiUnavailable] = useState(false);
  const [activeStatus, setActiveStatus] = useState(statusParam);
  const [popularTags, setPopularTags] = useState([]);

  // Ingestion Admin state
  const [ingesting, setIngesting] = useState(false);
  const [ingestNotification, setIngestNotification] = useState(null);

  // Fetch popular tags on mount
  useEffect(() => {
    knowledgeService.getTags()
      .then((tags) => setPopularTags(tags.slice(0, 10)))
      .catch((err) => console.error('Failed to load tags:', err));
  }, []);

  const loadArticles = useCallback(async () => {
    setLoading(true);
    setError(null);
    setAiUnavailable(false);

    try {
      if (queryParam) {
        if (searchType === 'SEMANTIC') {
          // Phase 11 Semantic Vector Search
          const data = await knowledgeService.semanticSearch({
            q: queryParam,
            category: categoryParam !== 'ALL' ? categoryParam : undefined,
            topK: 12,
            minSimilarity: 0.35,
          });

          if (data.available === false) {
            setAiUnavailable(true);
            setSemanticResults([]);
            setTotalElements(0);
          } else {
            setSemanticResults(data.results || []);
            setTotalElements(data.totalHits || (data.results ? data.results.length : 0));
            setTotalPages(1);
          }
          setArticles([]);
        } else {
          // Phase 10 Keyword Search
          const data = await knowledgeService.searchArticles({
            q: queryParam,
            page: currentPage,
            size: 9,
          });
          setArticles(data.articles || []);
          setSemanticResults([]);
          setTotalElements(data.totalHits || 0);
          setTotalPages(data.totalPages || 0);
        }
      } else {
        // Standard Filtered listing
        const data = await knowledgeService.getArticles({
          category: categoryParam !== 'ALL' ? categoryParam : undefined,
          status: isStaff ? activeStatus : 'PUBLISHED',
          tag: tagParam || undefined,
          page: currentPage,
          size: 9,
        });
        setArticles(data.content || []);
        setSemanticResults([]);
        setTotalElements(data.totalElements || 0);
        setTotalPages(data.totalPages || 0);
      }
    } catch (err) {
      console.error('Failed to load knowledge articles:', err);
      setError('Unable to load knowledge articles. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [queryParam, searchType, categoryParam, activeStatus, tagParam, currentPage, isStaff]);

  useEffect(() => {
    loadArticles();
  }, [loadArticles]);

  // Handlers
  const handleSearch = (q, type = searchType) => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    if (q) {
      params.set('q', q);
      params.set('type', type);
    } else {
      params.delete('q');
      params.delete('type');
    }
    params.set('page', '0');
    setSearchType(type);
    setSearchParams(params);
  };

  const handleSearchTypeChange = (newType) => {
    setSearchType(newType);
    if (queryParam) {
      const params = new URLSearchParams(searchParams);
      params.set('type', newType);
      params.set('page', '0');
      setSearchParams(params);
    }
  };

  const handleSelectCategory = (catId) => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    if (catId && catId !== 'ALL') {
      params.set('category', catId);
    } else {
      params.delete('category');
    }
    params.set('page', '0');
    setSearchParams(params);
  };

  const handleTagClick = (tag) => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    params.delete('q');
    params.set('tag', tag);
    params.set('page', '0');
    setSearchParams(params);
  };

  const handleClearTag = () => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    params.delete('tag');
    setSearchParams(params);
  };

  const handleStatusChange = (status) => {
    setActiveStatus(status);
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    params.set('status', status);
    params.set('page', '0');
    setSearchParams(params);
  };

  // Staff Ingestion Trigger
  const handleTriggerIngestion = async () => {
    if (!isStaff || ingesting) return;
    setIngesting(true);
    setIngestNotification(null);
    try {
      const res = await knowledgeService.runIngestion();
      setIngestNotification({
        type: 'success',
        message: `Ingestion completed: ${res.articlesProcessed} articles processed, ${res.chunksEmbedded} vector chunks stored.`,
      });
      loadArticles();
    } catch (err) {
      console.error('Ingestion failed:', err);
      setIngestNotification({
        type: 'danger',
        message: 'Knowledge ingestion failed. Verify that AI microservice is reachable.',
      });
    } finally {
      setIngesting(false);
    }
  };

  return (
    <div className="container-fluid py-4">
      {/* Page Header */}
      <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-3 mb-4">
        <div>
          <div className="d-flex align-items-center gap-2">
            <h2 className="text-white fw-bold mb-0">
              <i className="bi bi-journal-bookmark-fill text-primary me-2"></i>
              Knowledge Base
            </h2>
            <span className="badge bg-info bg-opacity-25 text-info border border-info border-opacity-25">
              Phase 11 Vector Search
            </span>
          </div>
          <p className="text-secondary small mb-0 mt-1">
            Browse verified troubleshooting guides, self-service solutions, and semantic vector embeddings.
          </p>
        </div>

        {/* Staff Action Controls */}
        {isStaff && (
          <div className="d-flex align-items-center gap-2">
            <button
              type="button"
              className="btn btn-outline-info d-inline-flex align-items-center gap-2"
              onClick={handleTriggerIngestion}
              disabled={ingesting}
              title="Runs batch chunking and vector embedding generation for pending articles"
            >
              {ingesting ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status"></span>
                  <span>Ingesting Chunks...</span>
                </>
              ) : (
                <>
                  <i className="bi bi-cpu"></i>
                  <span>Run AI Ingestion</span>
                </>
              )}
            </button>

            <Link to="/knowledge/articles/new" className="btn btn-primary d-inline-flex align-items-center gap-2">
              <i className="bi bi-plus-lg"></i>
              <span>Create Article</span>
            </Link>
          </div>
        )}
      </div>

      {/* Staff Ingestion Alert Notification */}
      {ingestNotification && (
        <div className={`alert alert-${ingestNotification.type} alert-dismissible fade show mb-4`} role="alert">
          <i className="bi bi-info-circle-fill me-2"></i>
          {ingestNotification.message}
          <button
            type="button"
            className="btn-close"
            onClick={() => setIngestNotification(null)}
          ></button>
        </div>
      )}

      {/* Main Search Hero Bar */}
      <div className="card bg-dark border-secondary p-4 mb-4 shadow-sm">
        <div className="row justify-content-center">
          <div className="col-12 col-lg-9">
            <KnowledgeSearchBar
              initialValue={queryParam}
              searchType={searchType}
              onSearch={handleSearch}
              onSearchTypeChange={handleSearchTypeChange}
            />

            {/* Popular tags row */}
            {popularTags.length > 0 && (
              <div className="d-flex align-items-center flex-wrap gap-2 mt-3 small text-secondary">
                <span className="fw-semibold text-light">
                  <i className="bi bi-lightning-charge text-warning me-1"></i> Popular Tags:
                </span>
                {popularTags.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    className={`btn btn-sm btn-outline-secondary py-0 px-2 rounded-pill ${
                      tagParam === tag ? 'active bg-primary border-primary text-white' : 'text-secondary'
                    }`}
                    style={{ fontSize: '0.75rem' }}
                    onClick={() => handleTagClick(tag)}
                  >
                    #{tag}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Staff Status Navigation Tabs */}
      {isStaff && !queryParam && (
        <div className="d-flex gap-2 border-bottom border-secondary border-opacity-50 pb-2 mb-3">
          <button
            type="button"
            className={`btn btn-sm ${activeStatus === 'PUBLISHED' ? 'btn-primary' : 'btn-dark text-secondary'}`}
            onClick={() => handleStatusChange('PUBLISHED')}
          >
            <i className="bi bi-check-circle me-1"></i> Published
          </button>
          <button
            type="button"
            className={`btn btn-sm ${activeStatus === 'DRAFT' ? 'btn-warning text-dark fw-semibold' : 'btn-dark text-secondary'}`}
            onClick={() => handleStatusChange('DRAFT')}
          >
            <i className="bi bi-pencil me-1"></i> Drafts
          </button>
          <button
            type="button"
            className={`btn btn-sm ${activeStatus === 'ARCHIVED' ? 'btn-secondary text-white' : 'btn-dark text-secondary'}`}
            onClick={() => handleStatusChange('ARCHIVED')}
          >
            <i className="bi bi-archive me-1"></i> Archived
          </button>
        </div>
      )}

      {/* Active Filter Indicators & Search Mode Badge */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div className="d-flex align-items-center flex-wrap gap-2">
          {queryParam && (
            <>
              <span className={`badge ${searchType === 'SEMANTIC' ? 'bg-info text-dark' : 'bg-primary'} d-inline-flex align-items-center gap-2 px-3 py-2`}>
                <i className={searchType === 'SEMANTIC' ? 'bi bi-cpu-fill' : 'bi bi-search'}></i>
                Search Type: <strong>{searchType === 'SEMANTIC' ? 'Semantic (AI Vector)' : 'Keyword'}</strong>
              </span>

              <span className="badge bg-dark border border-secondary text-light d-inline-flex align-items-center gap-2 px-3 py-2">
                Query: &quot;{queryParam}&quot;
                <button
                  type="button"
                  className="btn-close btn-close-white ms-1"
                  style={{ fontSize: '0.65rem' }}
                  onClick={() => handleSearch('')}
                ></button>
              </span>
            </>
          )}

          {tagParam && (
            <span className="badge bg-info text-dark d-inline-flex align-items-center gap-2 px-3 py-2">
              <i className="bi bi-tag-fill"></i>
              Tag: #{tagParam}
              <button
                type="button"
                className="btn-close ms-1"
                style={{ fontSize: '0.65rem' }}
                onClick={handleClearTag}
              ></button>
            </span>
          )}

          <span className="text-secondary small">
            Found <strong>{totalElements}</strong> {queryParam && searchType === 'SEMANTIC' ? (totalElements === 1 ? 'matching chunk' : 'matching chunks') : (totalElements === 1 ? 'article' : 'articles')}
          </span>
        </div>

        {/* Categories Bar */}
        <KnowledgeCategoryFilter
          selectedCategory={categoryParam}
          onSelectCategory={handleSelectCategory}
        />
      </div>

      {/* AI Vector Service Offline Alert */}
      {aiUnavailable && (
        <div className="alert alert-warning bg-warning bg-opacity-10 border-warning border-opacity-25 text-warning d-flex align-items-center justify-content-between mb-4">
          <div>
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            Semantic search is temporarily unavailable. Keyword search remains fully operational.
          </div>
          <button
            type="button"
            className="btn btn-sm btn-outline-warning"
            onClick={() => handleSearch(queryParam, 'KEYWORD')}
          >
            Switch to Keyword Search
          </button>
        </div>
      )}

      {/* Content Area */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
          <div className="text-secondary small mt-2">
            {queryParam && searchType === 'SEMANTIC'
              ? 'Computing query embeddings and cosine distance...'
              : 'Loading knowledge articles...'}
          </div>
        </div>
      ) : error ? (
        <div className="alert alert-danger bg-danger bg-opacity-10 border-danger border-opacity-25 text-danger">
          <i className="bi bi-exclamation-triangle-fill me-2"></i> {error}
        </div>
      ) : (queryParam && searchType === 'SEMANTIC') ? (
        // Render Semantic Vector Search Results
        semanticResults.length === 0 ? (
          <div className="card bg-dark border-secondary text-center py-5 p-4">
            <i className="bi bi-cpu text-secondary fs-1 mb-3"></i>
            <h4 className="text-white">No semantic matches found</h4>
            <p className="text-secondary small mb-3">
              No knowledge chunks met the minimum similarity threshold for &quot;{queryParam}&quot;.
              Try rephrasing your question or switch to keyword search.
            </p>
            <div className="d-flex justify-content-center gap-2">
              <button
                type="button"
                className="btn btn-outline-primary btn-sm"
                onClick={() => handleSearch(queryParam, 'KEYWORD')}
              >
                Try Keyword Search
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary text-light btn-sm"
                onClick={() => handleSearch('')}
              >
                Clear Search
              </button>
            </div>
          </div>
        ) : (
          <div className="row g-4">
            {semanticResults.map((result) => (
              <div key={result.chunkId} className="col-12 col-md-6 col-lg-6">
                <SemanticSearchResultCard result={result} />
              </div>
            ))}
          </div>
        )
      ) : articles.length === 0 ? (
        // Empty state for regular / keyword search
        <div className="card bg-dark border-secondary text-center py-5 p-4">
          <i className="bi bi-journal-x fs-1 text-secondary mb-3"></i>
          <h4 className="text-white">No knowledge articles found</h4>
          <p className="text-secondary small mb-3">
            {queryParam
              ? `No articles matched your keyword query "${queryParam}". Try searching with semantic search or alternate terms.`
              : 'There are currently no articles matching the selected category and status filters.'}
          </p>
          <div className="d-flex justify-content-center gap-2">
            {queryParam && (
              <button
                type="button"
                className="btn btn-outline-info btn-sm"
                onClick={() => handleSearch(queryParam, 'SEMANTIC')}
              >
                Try Semantic Search (AI)
              </button>
            )}
            {(queryParam || tagParam || categoryParam !== 'ALL') && (
              <button
                type="button"
                className="btn btn-outline-secondary text-light btn-sm"
                onClick={() => {
                  setSearchParams(new URLSearchParams());
                  setActiveStatus('PUBLISHED');
                  setCurrentPage(0);
                }}
              >
                Clear All Filters
              </button>
            )}
            {isStaff && (
              <Link to="/knowledge/articles/new" className="btn btn-primary btn-sm">
                Create First Article
              </Link>
            )}
          </div>
        </div>
      ) : (
        // Render Standard / Keyword Article Cards
        <>
          <div className="row g-4">
            {articles.map((article) => (
              <div key={article.id} className="col-12 col-md-6 col-lg-4">
                <KnowledgeArticleCard
                  article={article}
                  showStatus={isStaff}
                  onTagClick={handleTagClick}
                />
              </div>
            ))}
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="d-flex justify-content-between align-items-center mt-4 pt-3 border-top border-secondary border-opacity-50">
              <span className="text-secondary small">
                Page {currentPage + 1} of {totalPages}
              </span>
              <div className="d-flex gap-2">
                <button
                  type="button"
                  className="btn btn-outline-secondary text-light btn-sm"
                  disabled={currentPage === 0}
                  onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                >
                  <i className="bi bi-chevron-left me-1"></i> Previous
                </button>
                <button
                  type="button"
                  className="btn btn-outline-secondary text-light btn-sm"
                  disabled={currentPage >= totalPages - 1}
                  onClick={() => setCurrentPage((p) => p + 1)}
                >
                  Next <i className="bi bi-chevron-right ms-1"></i>
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default KnowledgeBase;
