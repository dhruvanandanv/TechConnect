import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import knowledgeService from '../services/knowledgeService';
import { formatEnum } from '../utils/formatters';

const CATEGORIES = [
  'HARDWARE',
  'SOFTWARE',
  'NETWORK',
  'SECURITY',
  'ACCESS_MANAGEMENT',
  'EMAIL',
  'VPN',
  'OTHER',
];

const KnowledgeArticleEditor = () => {
  const { id } = useParams();
  const isEditing = Boolean(id);
  const navigate = useNavigate();
  const { currentUser } = useAuth();

  const [formData, setFormData] = useState({
    title: '',
    category: 'SOFTWARE',
    tags: '',
    summary: '',
    problem: '',
    cause: '',
    resolution: '',
    sourceTicketId: '',
  });

  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [articleStatus, setArticleStatus] = useState('DRAFT');

  // Prepopulate if editing
  useEffect(() => {
    if (!isEditing) return;

    setLoading(true);
    knowledgeService.getArticleById(id)
      .then((article) => {
        setFormData({
          title: article.title || '',
          category: article.category || 'SOFTWARE',
          tags: (article.tags || []).join(', '),
          summary: article.summary || '',
          problem: article.problem || '',
          cause: article.cause || '',
          resolution: article.resolution || '',
          sourceTicketId: article.sourceTicketId || '',
        });
        setArticleStatus(article.status);
      })
      .catch((err) => {
        console.error('Failed to load article for edit:', err);
        setError(err.response?.data?.message || 'Failed to load article');
      })
      .finally(() => setLoading(false));
  }, [id, isEditing]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const parseTags = (tagString) => {
    if (!tagString) return [];
    return tagString
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t.length > 0);
  };

  const handleSubmit = async (targetStatus) => {
    setError(null);

    // Client-side validation
    if (!formData.title.trim()) {
      setError('Article title is required.');
      return;
    }
    if (!formData.summary.trim()) {
      setError('Article summary is required.');
      return;
    }
    if (!formData.problem.trim()) {
      setError('Problem and symptoms description is required.');
      return;
    }
    if (!formData.resolution.trim()) {
      setError('Resolution steps are required.');
      return;
    }

    setSubmitting(true);

    const payload = {
      title: formData.title.trim(),
      category: formData.category,
      tags: parseTags(formData.tags),
      summary: formData.summary.trim(),
      problem: formData.problem.trim(),
      cause: formData.cause ? formData.cause.trim() : null,
      resolution: formData.resolution.trim(),
      sourceTicketId: formData.sourceTicketId ? parseInt(formData.sourceTicketId, 10) : null,
    };

    try {
      if (isEditing) {
        // Update existing article
        const updated = await knowledgeService.updateArticle(id, payload);
        // If user explicitly chose to publish during edit
        if (targetStatus === 'PUBLISHED' && articleStatus !== 'PUBLISHED') {
          await knowledgeService.publishArticle(id);
        }
        navigate(`/knowledge/articles/${updated.id}`);
      } else {
        // Create new article
        payload.status = targetStatus || 'DRAFT';
        const created = await knowledgeService.createArticle(payload);
        navigate(`/knowledge/articles/${created.id}`);
      }
    } catch (err) {
      console.error('Save article error:', err);
      const backendErrors = err.response?.data?.errors;
      if (backendErrors && Array.isArray(backendErrors)) {
        setError(backendErrors.join(' | '));
      } else {
        setError(err.response?.data?.message || 'Failed to save knowledge article');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="container-fluid py-5 text-center">
        <div className="spinner-border text-primary" role="status"></div>
        <div className="text-secondary small mt-2">Loading article editor...</div>
      </div>
    );
  }

  return (
    <div className="container-fluid py-4 max-w-4xl">
      {/* Header */}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb mb-1">
              <li className="breadcrumb-item">
                <Link to="/knowledge" className="text-decoration-none text-secondary">
                  Knowledge Base
                </Link>
              </li>
              <li className="breadcrumb-item active text-light">
                {isEditing ? 'Edit Article' : 'New Article'}
              </li>
            </ol>
          </nav>
          <h2 className="text-white fw-bold mb-0">
            <i className={`bi ${isEditing ? 'bi-pencil-square' : 'bi-plus-circle-fill'} text-primary me-2`}></i>
            {isEditing ? 'Edit Knowledge Article' : 'Create Knowledge Article'}
          </h2>
        </div>

        <Link
          to={isEditing ? `/knowledge/articles/${id}` : '/knowledge'}
          className="btn btn-outline-secondary btn-sm"
        >
          Cancel
        </Link>
      </div>

      {error && (
        <div className="alert alert-danger bg-danger bg-opacity-10 border-danger text-danger mb-4">
          <i className="bi bi-exclamation-triangle-fill me-2"></i> {error}
        </div>
      )}

      {/* Form Container */}
      <div className="card bg-dark border-secondary p-4 shadow-sm">
        <form onSubmit={(e) => e.preventDefault()}>
          {/* Row 1: Title */}
          <div className="mb-3">
            <label className="form-label text-light fw-semibold">
              Article Title <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              name="title"
              className="form-control bg-dark text-light border-secondary"
              placeholder="e.g. How to Resolve Cisco AnyConnect VPN Gateway Disconnections"
              value={formData.title}
              onChange={handleChange}
              maxLength={200}
              required
            />
            <div className="form-text text-secondary small">
              A concise, descriptive summary of the problem or resolution procedure (max 200 characters).
            </div>
          </div>

          {/* Row 2: Category, Tags, Source Ticket */}
          <div className="row g-3 mb-3">
            <div className="col-12 col-md-4">
              <label className="form-label text-light fw-semibold">
                Category <span className="text-danger">*</span>
              </label>
              <select
                name="category"
                className="form-select bg-dark text-light border-secondary"
                value={formData.category}
                onChange={handleChange}
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {formatEnum(cat)}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-12 col-md-5">
              <label className="form-label text-light fw-semibold">
                Tags
              </label>
              <input
                type="text"
                name="tags"
                className="form-control bg-dark text-light border-secondary"
                placeholder="vpn, cisco, anyconnect, windows"
                value={formData.tags}
                onChange={handleChange}
              />
              <div className="form-text text-secondary small">Comma-separated tags</div>
            </div>

            <div className="col-12 col-md-3">
              <label className="form-label text-light fw-semibold">
                Source Ticket ID
              </label>
              <input
                type="number"
                name="sourceTicketId"
                className="form-control bg-dark text-light border-secondary"
                placeholder="e.g. 42"
                value={formData.sourceTicketId}
                onChange={handleChange}
              />
              <div className="form-text text-secondary small">Optional linkage</div>
            </div>
          </div>

          {/* Row 3: Summary */}
          <div className="mb-3">
            <label className="form-label text-light fw-semibold">
              Executive Summary <span className="text-danger">*</span>
            </label>
            <textarea
              name="summary"
              rows={2}
              className="form-control bg-dark text-light border-secondary"
              placeholder="Brief overview explaining when to use this troubleshooting guide..."
              value={formData.summary}
              onChange={handleChange}
              maxLength={500}
              required
            />
          </div>

          {/* Row 4: Problem & Symptoms */}
          <div className="mb-3">
            <label className="form-label text-light fw-semibold d-flex align-items-center gap-2">
              <i className="bi bi-exclamation-triangle text-warning"></i>
              Problem Description & Observed Symptoms <span className="text-danger">*</span>
            </label>
            <textarea
              name="problem"
              rows={4}
              className="form-control bg-dark text-light border-secondary"
              placeholder="Detail error codes, user observations, operating systems, and conditions under which the failure manifests..."
              value={formData.problem}
              onChange={handleChange}
              required
            />
          </div>

          {/* Row 5: Possible Cause */}
          <div className="mb-3">
            <label className="form-label text-light fw-semibold d-flex align-items-center gap-2">
              <i className="bi bi-search text-info"></i>
              Root Cause / Technical Diagnostics
            </label>
            <textarea
              name="cause"
              rows={3}
              className="form-control bg-dark text-light border-secondary"
              placeholder="Explain underlying causes (e.g. expired certificate, corrupted cache, port conflict)..."
              value={formData.cause}
              onChange={handleChange}
            />
          </div>

          {/* Row 6: Resolution Steps */}
          <div className="mb-4">
            <label className="form-label text-light fw-semibold d-flex align-items-center gap-2">
              <i className="bi bi-check-circle text-success"></i>
              Resolution & Step-by-Step Troubleshooting <span className="text-danger">*</span>
            </label>
            <textarea
              name="resolution"
              rows={7}
              className="form-control bg-dark text-light border-secondary"
              placeholder={"1. Open Command Prompt as Administrator\n2. Run 'netsh winsock reset'\n3. Reboot the machine\n4. Re-authenticate via SSO..."}
              value={formData.resolution}
              onChange={handleChange}
              required
            />
          </div>

          {/* Form Actions */}
          <div className="d-flex justify-content-between align-items-center pt-3 border-top border-secondary border-opacity-50">
            <Link
              to={isEditing ? `/knowledge/articles/${id}` : '/knowledge'}
              className="btn btn-outline-secondary text-light"
            >
              Cancel
            </Link>

            <div className="d-flex gap-2">
              {isEditing ? (
                <>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={submitting}
                    onClick={() => handleSubmit(articleStatus)}
                  >
                    {submitting ? 'Saving...' : 'Save Changes'}
                  </button>

                  {articleStatus !== 'PUBLISHED' && (
                    <button
                      type="button"
                      className="btn btn-success"
                      disabled={submitting}
                      onClick={() => handleSubmit('PUBLISHED')}
                    >
                      {submitting ? 'Publishing...' : 'Save & Publish'}
                    </button>
                  )}
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className="btn btn-outline-warning"
                    disabled={submitting}
                    onClick={() => handleSubmit('DRAFT')}
                  >
                    {submitting ? 'Saving...' : 'Save Draft'}
                  </button>

                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={submitting}
                    onClick={() => handleSubmit('PUBLISHED')}
                  >
                    {submitting ? 'Publishing...' : 'Publish Article'}
                  </button>
                </>
              )}
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};

export default KnowledgeArticleEditor;
