import React, { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import knowledgeService from '../services/knowledgeService';
import KnowledgeSearchBar from '../components/KnowledgeSearchBar';
import KnowledgeCategoryFilter from '../components/KnowledgeCategoryFilter';
import KnowledgeArticleCard from '../components/KnowledgeArticleCard';

const KnowledgeBase = () => {
  const { currentUser } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const isStaff = currentUser && ['ROLE_ENGINEER', 'ROLE_MANAGER', 'ROLE_ADMIN'].includes(currentUser.role);

  // URL state
  const queryParam = searchParams.get('q') || '';
  const categoryParam = searchParams.get('category') || 'ALL';
  const statusParam = searchParams.get('status') || (isStaff ? 'PUBLISHED' : 'PUBLISHED');
  const tagParam = searchParams.get('tag') || '';
  const pageParam = parseInt(searchParams.get('page') || '0', 10);

  // Component state
  const [articles, setArticles] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(pageParam);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeStatus, setActiveStatus] = useState(statusParam);
  const [popularTags, setPopularTags] = useState([]);

  // Fetch popular tags on mount
  useEffect(() => {
    knowledgeService.getTags()
      .then((tags) => setPopularTags(tags.slice(0, 10)))
      .catch((err) => console.error('Failed to load tags:', err));
  }, []);

  const loadArticles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (queryParam) {
        // Keyword Search
        const data = await knowledgeService.searchArticles({
          q: queryParam,
          page: currentPage,
          size: 9,
        });
        setArticles(data.articles || []);
        setTotalElements(data.totalHits || 0);
        setTotalPages(data.totalPages || 0);
      } else {
        // Filtered listing
        const data = await knowledgeService.getArticles({
          category: categoryParam !== 'ALL' ? categoryParam : undefined,
          status: isStaff ? activeStatus : 'PUBLISHED',
          tag: tagParam || undefined,
          page: currentPage,
          size: 9,
        });
        setArticles(data.content || []);
        setTotalElements(data.totalElements || 0);
        setTotalPages(data.totalPages || 0);
      }
    } catch (err) {
      console.error('Failed to load knowledge articles:', err);
      setError('Unable to load knowledge articles. Please try again.');
    } finally {
      setLoading(false);
    }
  }, [queryParam, categoryParam, activeStatus, tagParam, currentPage, isStaff]);

  useEffect(() => {
    loadArticles();
  }, [loadArticles]);

  // Handlers
  const handleSearch = (q) => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    if (q) {
      params.set('q', q);
    } else {
      params.delete('q');
    }
    params.set('page', '0');
    setSearchParams(params);
  };

  const handleSelectCategory = (catId) => {
    setCurrentPage(0);
    const params = new URLSearchParams(searchParams);
    params.delete('q'); // Clear search when picking category
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
            <span className="badge bg-secondary bg-opacity-25 text-light">
              Phase 10 ITSM
            </span>
          </div>
          <p className="text-secondary small mb-0 mt-1">
            Browse verified troubleshooting guides, self-service solutions, and IT SOPs.
          </p>
        </div>

        {isStaff && (
          <Link to="/knowledge/articles/new" className="btn btn-primary d-inline-flex align-items-center gap-2">
            <i className="bi bi-plus-lg"></i>
            <span>Create Article</span>
          </Link>
        )}
      </div>

      {/* Main Search Hero Bar */}
      <div className="card bg-dark border-secondary p-4 mb-4 shadow-sm">
        <div className="row justify-content-center">
          <div className="col-12 col-lg-9">
            <KnowledgeSearchBar initialValue={queryParam} onSearch={handleSearch} />

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
      {isStaff && (
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

      {/* Active Filter Indicators */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div className="d-flex align-items-center flex-wrap gap-2">
          {queryParam && (
            <span className="badge bg-primary d-inline-flex align-items-center gap-2 px-3 py-2">
              <i className="bi bi-search"></i>
              Query: &quot;{queryParam}&quot;
              <button
                type="button"
                className="btn-close btn-close-white ms-1"
                style={{ fontSize: '0.65rem' }}
                onClick={() => handleSearch('')}
              ></button>
            </span>
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
            Found <strong>{totalElements}</strong> {totalElements === 1 ? 'article' : 'articles'}
          </span>
        </div>

        {/* Categories Bar */}
        <KnowledgeCategoryFilter
          selectedCategory={categoryParam}
          onSelectCategory={handleSelectCategory}
        />
      </div>

      {/* Content Area */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
          <div className="text-secondary small mt-2">Loading knowledge articles...</div>
        </div>
      ) : error ? (
        <div className="alert alert-danger bg-danger bg-opacity-10 border-danger border-opacity-25 text-danger">
          <i className="bi bi-exclamation-triangle-fill me-2"></i> {error}
        </div>
      ) : articles.length === 0 ? (
        <div className="card bg-dark border-secondary text-center py-5 p-4">
          <i className="bi bi-journal-x fs-1 text-secondary mb-3"></i>
          <h4 className="text-white">No knowledge articles found</h4>
          <p className="text-secondary small mb-3">
            {queryParam
              ? `No articles matched your keyword query "${queryParam}". Try searching with alternate terminology or clear filters.`
              : 'There are currently no articles matching the selected category and status filters.'}
          </p>
          <div className="d-flex justify-content-center gap-2">
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
