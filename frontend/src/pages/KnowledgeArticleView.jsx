import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import knowledgeService from '../services/knowledgeService';
import KnowledgeArticleStatusBadge from '../components/KnowledgeArticleStatusBadge';
import KnowledgeFeedback from '../components/KnowledgeFeedback';
import KnowledgeHistoryModal from '../components/KnowledgeHistoryModal';
import { formatDate, formatEnum } from '../utils/formatters';

const KnowledgeArticleView = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [article, setArticle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  // History modal state
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyRecords, setHistoryRecords] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const isStaff = currentUser && ['ROLE_ENGINEER', 'ROLE_MANAGER', 'ROLE_ADMIN'].includes(currentUser.role);
  const isManagerOrAdmin = currentUser && ['ROLE_MANAGER', 'ROLE_ADMIN'].includes(currentUser.role);
  const isAuthor = article && currentUser && article.authorId === currentUser.id;
  const canManage = isManagerOrAdmin || isAuthor;

  useEffect(() => {
    setLoading(true);
    setError(null);
    knowledgeService.getArticleById(id)
      .then((data) => {
        setArticle(data);
      })
      .catch((err) => {
        console.error('Failed to load article:', err);
        if (err.response?.status === 403) {
          setError('Access Denied: This article is unpublished or in draft review.');
        } else if (err.response?.status === 404) {
          setError('Article not found or has been removed.');
        } else {
          setError('Failed to load knowledge article. Please try again later.');
        }
      })
      .finally(() => setLoading(false));
  }, [id]);

  const handlePublish = async () => {
    if (!window.confirm('Publish this knowledge article for all employees to access?')) return;
    setActionLoading(true);
    try {
      const updated = await knowledgeService.publishArticle(article.id);
      setArticle(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to publish article');
    } finally {
      setActionLoading(false);
    }
  };

  const handleArchive = async () => {
    if (!window.confirm('Archive this knowledge article? It will no longer be visible to employees.')) return;
    setActionLoading(true);
    try {
      const updated = await knowledgeService.archiveArticle(article.id);
      setArticle(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to archive article');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRevertDraft = async () => {
    if (!window.confirm('Revert this article to DRAFT status for editing?')) return;
    setActionLoading(true);
    try {
      const updated = await knowledgeService.revertToDraft(article.id);
      setArticle(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to revert article to draft');
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenHistory = async () => {
    setHistoryOpen(true);
    setHistoryLoading(true);
    try {
      const records = await knowledgeService.getArticleHistory(article.id);
      setHistoryRecords(records);
    } catch (err) {
      console.error('Failed to load history:', err);
    } finally {
      setHistoryLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="container-fluid py-5 text-center">
        <div className="spinner-border text-primary" role="status"></div>
        <div className="text-secondary small mt-2">Loading knowledge article...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container-fluid py-5">
        <div className="card bg-dark border-secondary p-5 text-center max-w-lg mx-auto">
          <i className="bi bi-shield-lock-fill text-warning fs-1 mb-3"></i>
          <h4 className="text-white mb-2">{error}</h4>
          <p className="text-secondary small mb-4">
            If you believe you should have access to this resource, contact your system administrator.
          </p>
          <div>
            <Link to="/knowledge" className="btn btn-primary btn-sm">
              <i className="bi bi-arrow-left me-1"></i> Back to Knowledge Base
            </Link>
          </div>
        </div>
      </div>
    );
  }

  if (!article) return null;

  return (
    <div className="container-fluid py-4 max-w-5xl">
      {/* Breadcrumb Navigation */}
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/knowledge" className="text-decoration-none text-secondary">
              <i className="bi bi-journal-bookmark me-1"></i> Knowledge Base
            </Link>
          </li>
          <li className="breadcrumb-item">
            <Link
              to={`/knowledge?category=${article.category}`}
              className="text-decoration-none text-secondary"
            >
              {formatEnum(article.category)}
            </Link>
          </li>
          <li className="breadcrumb-item active text-light text-truncate" style={{ maxWidth: '300px' }}>
            {article.title}
          </li>
        </ol>
      </nav>

      {/* Main Header Card */}
      <div className="card bg-dark border-secondary p-4 mb-4 shadow-sm">
        <div className="d-flex flex-column flex-md-row justify-content-between align-items-start gap-3">
          <div className="flex-grow-1">
            <div className="d-flex align-items-center flex-wrap gap-2 mb-2">
              <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1">
                {formatEnum(article.category)}
              </span>
              <span className="badge bg-secondary bg-opacity-25 text-light px-2 py-1">
                v{article.version}
              </span>
              <KnowledgeArticleStatusBadge status={article.status} />
              {article.sourceType === 'TICKET' && article.sourceTicketId && (
                <span className="badge bg-info bg-opacity-10 text-info border border-info border-opacity-25 px-2 py-1">
                  <i className="bi bi-link-45deg me-1"></i>
                  Ticket #{article.sourceTicketId}
                </span>
              )}
            </div>

            <h2 className="text-white fw-bold mb-2">{article.title}</h2>

            <div className="d-flex align-items-center flex-wrap gap-3 text-secondary small">
              <div>
                <i className="bi bi-person-fill text-primary me-1"></i>
                <span>Author: <strong>{article.authorName}</strong></span>
              </div>
              <div>
                <i className="bi bi-clock-history me-1"></i>
                <span>Last Updated: {formatDate(article.updatedAt || article.publishedAt)}</span>
              </div>
              <div>
                <i className="bi bi-eye me-1"></i>
                <span>{article.viewCount} views</span>
              </div>
            </div>
          </div>

          {/* Staff Action Controls */}
          {isStaff && (
            <div className="d-flex flex-wrap gap-2 align-self-start">
              {canManage && (
                <Link
                  to={`/knowledge/articles/${article.id}/edit`}
                  className="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                >
                  <i className="bi bi-pencil"></i>
                  <span>Edit</span>
                </Link>
              )}

              {canManage && article.status !== 'PUBLISHED' && (
                <button
                  type="button"
                  className="btn btn-success btn-sm d-inline-flex align-items-center gap-1"
                  onClick={handlePublish}
                  disabled={actionLoading}
                >
                  <i className="bi bi-check2-circle"></i>
                  <span>Publish</span>
                </button>
              )}

              {canManage && article.status === 'PUBLISHED' && (
                <button
                  type="button"
                  className="btn btn-outline-warning btn-sm d-inline-flex align-items-center gap-1"
                  onClick={handleRevertDraft}
                  disabled={actionLoading}
                >
                  <i className="bi bi-arrow-counterclockwise"></i>
                  <span>Draft</span>
                </button>
              )}

              {canManage && article.status !== 'ARCHIVED' && (
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm d-inline-flex align-items-center gap-1"
                  onClick={handleArchive}
                  disabled={actionLoading}
                >
                  <i className="bi bi-archive"></i>
                  <span>Archive</span>
                </button>
              )}

              {canManage && (
                <button
                  type="button"
                  className="btn btn-outline-info btn-sm d-inline-flex align-items-center gap-1"
                  onClick={handleOpenHistory}
                >
                  <i className="bi bi-clock-history"></i>
                  <span>History</span>
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Summary Alert */}
      <div className="alert alert-dark bg-dark border-secondary text-light mb-4">
        <div className="d-flex align-items-start gap-2">
          <i className="bi bi-info-circle-fill text-primary fs-5 mt-1"></i>
          <div>
            <div className="fw-semibold small text-primary text-uppercase letter-spacing-1">Summary</div>
            <div className="text-light mt-1">{article.summary}</div>
          </div>
        </div>
      </div>

      {/* Structured Troubleshooting Cards */}
      <div className="row g-4 mb-4">
        {/* Problem / Symptoms Card */}
        <div className="col-12">
          <div className="card bg-dark border-secondary">
            <div className="card-header border-secondary bg-dark d-flex align-items-center gap-2 py-3">
              <i className="bi bi-exclamation-triangle-fill text-warning fs-5"></i>
              <h5 className="text-white mb-0">Problem & Symptoms</h5>
            </div>
            <div className="card-body">
              <p className="card-text text-light whitespace-pre-wrap mb-0" style={{ whiteSpace: 'pre-wrap', lineHeight: '1.7' }}>
                {article.problem}
              </p>
            </div>
          </div>
        </div>

        {/* Possible Cause Card */}
        {article.cause && (
          <div className="col-12">
            <div className="card bg-dark border-secondary">
              <div className="card-header border-secondary bg-dark d-flex align-items-center gap-2 py-3">
                <i className="bi bi-search text-info fs-5"></i>
                <h5 className="text-white mb-0">Possible Cause / Diagnostic Analysis</h5>
              </div>
              <div className="card-body">
                <p className="card-text text-light whitespace-pre-wrap mb-0" style={{ whiteSpace: 'pre-wrap', lineHeight: '1.7' }}>
                  {article.cause}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Resolution Steps Card */}
        <div className="col-12">
          <div className="card bg-dark border-success border-opacity-50">
            <div className="card-header border-success border-opacity-25 bg-success bg-opacity-10 d-flex align-items-center justify-content-between py-3">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-check-circle-fill text-success fs-5"></i>
                <h5 className="text-white mb-0">Resolution & Troubleshooting Steps</h5>
              </div>
              <button
                type="button"
                className="btn btn-outline-success btn-sm py-0 px-2"
                onClick={() => {
                  navigator.clipboard.writeText(article.resolution);
                  alert('Resolution steps copied to clipboard!');
                }}
              >
                <i className="bi bi-clipboard me-1"></i> Copy Steps
              </button>
            </div>
            <div className="card-body">
              <div
                className="card-text text-light whitespace-pre-wrap mb-0"
                style={{ whiteSpace: 'pre-wrap', lineHeight: '1.8' }}
              >
                {article.resolution}
              </div>
            </div>
          </div>
        </div>

        {/* Source Ticket Linkage */}
        {article.sourceTicketId && (
          <div className="col-12">
            <div className="card bg-dark border-secondary p-3">
              <div className="d-flex align-items-center justify-content-between flex-wrap gap-2">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-ticket-detailed text-info fs-4"></i>
                  <div>
                    <div className="text-white fw-semibold small">Originating Service Ticket</div>
                    <div className="text-secondary small">
                      This solution was documented following resolution of Ticket #{article.sourceTicketId}.
                    </div>
                  </div>
                </div>
                <Link
                  to={`/tickets/${article.sourceTicketId}`}
                  className="btn btn-outline-info btn-sm"
                >
                  View Ticket #{article.sourceTicketId}
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Tags Row */}
      {article.tags && article.tags.length > 0 && (
        <div className="d-flex align-items-center flex-wrap gap-2 mb-4">
          <span className="text-secondary small fw-semibold me-1">
            <i className="bi bi-tags me-1"></i> Tags:
          </span>
          {article.tags.map((tag) => (
            <Link
              key={tag}
              to={`/knowledge?tag=${encodeURIComponent(tag)}`}
              className="badge bg-secondary bg-opacity-25 text-light text-decoration-none px-2 py-1"
            >
              #{tag}
            </Link>
          ))}
        </div>
      )}

      {/* Helpful Feedback Widget */}
      {article.status === 'PUBLISHED' && (
        <KnowledgeFeedback
          articleId={article.id}
          helpfulCount={article.helpfulCount}
          notHelpfulCount={article.notHelpfulCount}
          initialHasVoted={article.userHasVoted}
        />
      )}

      {/* History Modal */}
      <KnowledgeHistoryModal
        isOpen={historyOpen}
        onClose={() => setHistoryOpen(false)}
        history={historyRecords}
        loading={historyLoading}
      />
    </div>
  );
};

export default KnowledgeArticleView;
