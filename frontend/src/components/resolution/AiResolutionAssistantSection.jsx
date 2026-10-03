import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import resolutionService from '../../services/resolutionService';
import { extractErrorMessage } from '../../services/api';

/**
 * AI Resolution Assistant Section for TicketDetails page.
 * Accessible strictly by authorized support engineers, managers, and admins.
 * Synthesizes grounded troubleshooting and resolution proposals from
 * current ticket context, published knowledge base articles, and similar resolved tickets.
 *
 * CRITICAL SAFETY INVARIANT:
 * This component is strictly advisory. It NEVER autonomously modifies ticket status,
 * mutates SLAs, or triggers backend ticket actions.
 */
const AiResolutionAssistantSection = ({ ticket, onApplyResolution, isStaff }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [response, setResponse] = useState(null);
  const [copied, setCopied] = useState(false);
  const [appliedNotice, setAppliedNotice] = useState(false);

  if (!isStaff || !ticket) {
    return null;
  }

  const handleGenerateSuggestion = async () => {
    try {
      setLoading(true);
      setError(null);
      setAppliedNotice(false);

      const data = await resolutionService.getResolutionSuggestion(ticket.id, {
        topK: 5,
        minSimilarity: 0.30,
      });

      setResponse(data);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to generate resolution suggestion.'));
    } finally {
      setLoading(false);
    }
  };

  const formatFullSuggestionText = () => {
    if (!response) return '';
    let text = response.suggestion || '';
    if (response.steps && response.steps.length > 0) {
      text += '\n\nTroubleshooting & Resolution Steps:\n';
      text += response.steps.map((step, idx) => `${idx + 1}. ${step}`).join('\n');
    }
    return text;
  };

  const handleCopyToClipboard = () => {
    const fullText = formatFullSuggestionText();
    if (fullText) {
      navigator.clipboard.writeText(fullText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    }
  };

  const handleCopyToResolutionField = () => {
    const fullText = formatFullSuggestionText();
    if (onApplyResolution && fullText) {
      onApplyResolution(fullText);
      setAppliedNotice(true);
      setTimeout(() => setAppliedNotice(false), 4000);
    }
  };

  return (
    <div className="tc-card mb-4 border-start border-4 border-indigo shadow-sm" style={{ borderLeftColor: '#6366f1' }}>
      <div className="tc-card-body p-4">
        {/* Header Bar */}
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3 pb-3 border-bottom">
          <div className="d-flex align-items-center gap-2">
            <div
              className="rounded-circle d-flex align-items-center justify-content-center text-white"
              style={{ width: '40px', height: '40px', backgroundColor: '#6366f1' }}
            >
              <i className="bi bi-lightbulb-fill fs-5"></i>
            </div>
            <div>
              <div className="d-flex align-items-center gap-2">
                <h5 className="fw-bold mb-0 text-slate-900">AI Resolution Assistant</h5>
                <span className="badge bg-light text-primary border">Engineer Tool</span>
                {response && (
                  <span
                    className={`badge ${
                      response.grounded ? 'bg-success text-white' : 'bg-warning text-dark'
                    }`}
                  >
                    {response.grounded ? 'Grounded Proposal' : 'Ungrounded / Insufficient Context'}
                  </span>
                )}
              </div>
              <span className="text-muted small">
                Grounded ITIL resolution synthesis combining active ticket context, verified KB articles, and similar resolved tickets.
              </span>
            </div>
          </div>

          <div>
            <button
              type="button"
              className="btn btn-primary d-flex align-items-center gap-2"
              onClick={handleGenerateSuggestion}
              disabled={loading}
              id="generateResolutionSuggestionBtn"
              style={{ backgroundColor: '#6366f1', borderColor: '#4f46e5' }}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span>Synthesizing Suggestion...</span>
                </>
              ) : (
                <>
                  <i className="bi bi-magic"></i>
                  <span>{response ? 'Regenerate Suggestion' : 'Generate Resolution Suggestion'}</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Advisory Invariant Banner */}
        <div className="alert alert-light border py-2 px-3 mb-3 small text-muted d-flex align-items-center gap-2">
          <i className="bi bi-shield-lock-fill text-indigo fs-5" style={{ color: '#6366f1' }}></i>
          <span>
            <strong>Advisory Guidance Only:</strong> AI suggestions are synthesized recommendations for engineer review.
            The engineer remains solely responsible for verifying technical steps and executing manual ticket resolution.
          </span>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="alert alert-danger d-flex align-items-center justify-content-between p-3 mb-3">
            <div className="d-flex align-items-center gap-2">
              <i className="bi bi-exclamation-triangle-fill fs-5"></i>
              <span>{error}</span>
            </div>
            <button
              type="button"
              className="btn btn-sm btn-outline-danger"
              onClick={handleGenerateSuggestion}
            >
              Retry
            </button>
          </div>
        )}

        {/* Loading Skeleton */}
        {loading && (
          <div className="p-4 bg-light rounded text-center my-3">
            <div className="spinner-border text-primary mb-2" role="status" style={{ color: '#6366f1' }}>
              <span className="visually-hidden">Loading...</span>
            </div>
            <p className="text-slate-800 fw-medium mb-1">
              Analyzing ticket #{ticket.id} and querying vector indices...
            </p>
            <p className="text-muted small mb-0">
              Retrieving relevant knowledge chunks and matching historical resolved tickets.
            </p>
          </div>
        )}

        {/* Results Presentation */}
        {!loading && response && (
          <div className="resolution-assistant-results mt-3">
            {/* Visual Distinction: AI Suggestion Notice */}
            <div
              className="alert py-2 px-3 mb-3 d-flex align-items-center justify-content-between"
              style={{
                backgroundColor: '#f5f3ff',
                borderColor: '#ddd6fe',
                color: '#5b21b6',
              }}
            >
              <div className="d-flex align-items-center gap-2 small">
                <i className="bi bi-info-circle-fill fs-6"></i>
                <span>
                  <strong>AI SUGGESTION (UNAPPLIED):</strong> This proposal has not modified the ticket. Click <em>Copy to Resolution</em> to stage it for review.
                </span>
              </div>
              <div className="d-flex gap-2">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                  onClick={handleCopyToClipboard}
                  title="Copy text to clipboard"
                >
                  <i className={`bi ${copied ? 'bi-check-lg text-success' : 'bi-clipboard'}`}></i>
                  <span>{copied ? 'Copied' : 'Copy Text'}</span>
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-success d-flex align-items-center gap-1"
                  onClick={handleCopyToResolutionField}
                  id="copyToResolutionBtn"
                  title="Copy suggestion into ticket resolution form"
                >
                  <i className="bi bi-box-arrow-in-down-right"></i>
                  <span>Copy to Resolution</span>
                </button>
              </div>
            </div>

            {appliedNotice && (
              <div className="alert alert-success py-2 px-3 mb-3 small d-flex align-items-center gap-2">
                <i className="bi bi-check-circle-fill"></i>
                <span>
                  <strong>Staged in Resolution form!</strong> Click <em>Update Status</em> above to review and submit the resolution manually.
                </span>
              </div>
            )}

            {/* Main Suggestion Text */}
            <div className="p-3 bg-white border rounded shadow-sm mb-3">
              <h6 className="fw-bold text-slate-900 mb-2">Resolution Suggestion Overview</h6>
              <div
                className="text-slate-800"
                style={{ whiteSpace: 'pre-line', lineHeight: '1.6' }}
              >
                {response.suggestion}
              </div>
            </div>

            {/* Discrete Steps List */}
            {response.steps && response.steps.length > 0 && (
              <div className="mb-4">
                <h6 className="fw-bold text-slate-900 mb-2 d-flex align-items-center gap-2">
                  <i className="bi bi-list-check text-primary"></i>
                  <span>Recommended Action Steps</span>
                </h6>
                <ol className="list-group list-group-numbered shadow-sm">
                  {response.steps.map((step, idx) => (
                    <li
                      key={idx}
                      className="list-group-item d-flex justify-content-between align-items-start py-2 px-3"
                    >
                      <div className="ms-2 me-auto text-slate-800">
                        {step}
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
            )}

            {/* Retrieval Context Tabs / Accordion: Sources and Similar Tickets */}
            <div className="row g-3 mb-3">
              {/* Knowledge Base Sources */}
              <div className="col-12 col-lg-6">
                <div className="card h-100 border-0 bg-light shadow-sm">
                  <div className="card-header bg-transparent border-0 pb-0 d-flex justify-content-between align-items-center">
                    <span className="fw-bold text-slate-900 small d-flex align-items-center gap-1">
                      <i className="bi bi-journal-bookmark text-primary"></i>
                      Knowledge Articles Used ({response.sources ? response.sources.length : 0})
                    </span>
                    {response.retrievalMeta?.bestKnowledgeSimilarity && (
                      <span className="badge bg-white text-muted border small">
                        Max Sim: {Math.round(response.retrievalMeta.bestKnowledgeSimilarity * 100)}%
                      </span>
                    )}
                  </div>
                  <div className="card-body p-3">
                    {response.sources && response.sources.length > 0 ? (
                      <div className="d-flex flex-column gap-2">
                        {response.sources.map((src, idx) => (
                          <div key={idx} className="bg-white p-2 rounded border small">
                            <div className="d-flex justify-content-between align-items-start mb-1">
                              <span className="fw-semibold text-slate-900">{src.title || src.articleId}</span>
                              <span className="badge bg-success bg-opacity-10 text-success">
                                {Math.round((src.similarity || 0) * 100)}%
                              </span>
                            </div>
                            <div className="d-flex justify-content-between align-items-center text-muted">
                              <span>Section: <code>{src.section || 'RESOLUTION'}</code></span>
                              <Link
                                to={`/knowledge/articles/${src.articleId}`}
                                target="_blank"
                                rel="noreferrer"
                                className="text-primary text-decoration-none"
                              >
                                View KB <i className="bi bi-box-arrow-up-right"></i>
                              </Link>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-muted small mb-0">No matching knowledge articles retrieved above threshold.</p>
                    )}
                  </div>
                </div>
              </div>

              {/* Similar Resolved Historical Tickets */}
              <div className="col-12 col-lg-6">
                <div className="card h-100 border-0 bg-light shadow-sm">
                  <div className="card-header bg-transparent border-0 pb-0 d-flex justify-content-between align-items-center">
                    <span className="fw-bold text-slate-900 small d-flex align-items-center gap-1">
                      <i className="bi bi-clock-history text-secondary"></i>
                      Similar Resolved Tickets ({response.similarTickets ? response.similarTickets.length : 0})
                    </span>
                    {response.retrievalMeta?.bestTicketSimilarity && (
                      <span className="badge bg-white text-muted border small">
                        Max Sim: {Math.round(response.retrievalMeta.bestTicketSimilarity * 100)}%
                      </span>
                    )}
                  </div>
                  <div className="card-body p-3">
                    {response.similarTickets && response.similarTickets.length > 0 ? (
                      <div className="d-flex flex-column gap-2">
                        {response.similarTickets.map((t, idx) => (
                          <div key={idx} className="bg-white p-2 rounded border small">
                            <div className="d-flex justify-content-between align-items-start mb-1">
                              <span className="fw-semibold text-slate-900">
                                Ticket #{t.ticketId}
                              </span>
                              <span className="badge bg-info bg-opacity-10 text-info">
                                {Math.round((t.similarity || 0) * 100)}% Match
                              </span>
                            </div>
                            <div className="d-flex justify-content-between align-items-center text-muted">
                              <span>
                                {t.category && <span className="me-2">{t.category}</span>}
                                {t.priority && <span>{t.priority}</span>}
                              </span>
                              <Link
                                to={`/tickets/${t.ticketId}`}
                                target="_blank"
                                rel="noreferrer"
                                className="text-primary text-decoration-none"
                              >
                                View Ticket <i className="bi bi-box-arrow-up-right"></i>
                              </Link>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-muted small mb-0">No historical resolved tickets met similarity threshold.</p>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Model & Latency Metadata */}
            <div className="d-flex flex-wrap justify-content-between align-items-center text-muted small pt-2 border-top">
              <div>
                <span>Provider: <code>{response.provider || 'default'}</code></span>
                <span className="mx-2">•</span>
                <span>Model: <code>{response.model || 'grounded-extractive-v1'}</code></span>
              </div>
              {response.processingTimeMs > 0 && (
                <div>
                  <i className="bi bi-speedometer2 me-1"></i>
                  Synthesis Latency: {response.processingTimeMs}ms
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AiResolutionAssistantSection;
