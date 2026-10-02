import React from 'react';

const AiTicketAnalysisCard = ({
  analysis,
  loading,
  onApplyCategory,
  onApplyPriority,
  onApplyAll,
  onDismiss,
}) => {
  if (loading) {
    return (
      <div className="card border-primary border-opacity-25 shadow-sm mb-4 bg-primary bg-opacity-10">
        <div className="card-body p-4 text-center">
          <div className="spinner-border text-primary mb-2" role="status" style={{ width: '2rem', height: '2rem' }}>
            <span className="visually-hidden">Analyzing...</span>
          </div>
          <h6 className="fw-bold text-slate-800 mb-1">Analyzing with Ticket Intelligence AI...</h6>
          <p className="text-muted small mb-0">
            Evaluating category semantics, operational priority signals, and team routing rules.
          </p>
        </div>
      </div>
    );
  }

  if (!analysis) {
    return null;
  }

  // Graceful Fallback Alert when AI is unavailable
  if (!analysis.aiAvailable) {
    return (
      <div className="alert alert-warning border-warning border-opacity-50 shadow-sm d-flex align-items-start mb-4" role="alert">
        <i className="bi bi-exclamation-triangle-fill fs-4 text-warning me-3 mt-1"></i>
        <div className="flex-grow-1">
          <h6 className="alert-heading fw-bold mb-1">AI Intelligence Service Unavailable</h6>
          <p className="mb-0 small text-slate-700">
            {analysis.message || 'AI ticket analysis is currently offline. You can proceed with standard manual ticket creation.'}
          </p>
        </div>
        {onDismiss && (
          <button
            type="button"
            className="btn-close ms-2"
            aria-label="Dismiss alert"
            onClick={onDismiss}
          ></button>
        )}
      </div>
    );
  }

  const categoryScore = analysis.category;
  const priorityScore = analysis.priority;
  const teamScore = analysis.suggested_team || analysis.suggestedTeam;
  const reasons = analysis.reasons || [];

  return (
    <div className="card border-info border-opacity-50 shadow-sm mb-4 bg-light">
      <div className="card-header bg-white border-bottom d-flex justify-content-between align-items-center py-2 px-3">
        <div className="d-flex align-items-center gap-2">
          <span className="badge bg-primary text-white d-flex align-items-center gap-1">
            <i className="bi bi-robot"></i> AI Ticket Intelligence
          </span>
          {analysis.model_version && (
            <span className="badge bg-secondary bg-opacity-10 text-secondary border small">
              {analysis.model_version}
            </span>
          )}
          {analysis.processing_time_ms !== undefined && (
            <span className="text-muted small">
              <i className="bi bi-stopwatch me-1"></i>{analysis.processing_time_ms}ms
            </span>
          )}
        </div>
        {onDismiss && (
          <button
            type="button"
            className="btn-close btn-sm"
            aria-label="Dismiss analysis"
            onClick={onDismiss}
          ></button>
        )}
      </div>

      <div className="card-body p-3">
        <div className="alert alert-info py-2 px-3 mb-3 d-flex align-items-center gap-2 small">
          <i className="bi bi-info-circle-fill text-info flex-shrink-0"></i>
          <span>
            <strong>Human Decision Authority:</strong> AI suggestions are recommendations to assist triage. Your selected values remain authoritative.
          </span>
        </div>

        {/* Prediction Cards Row */}
        <div className="row g-2 mb-3">
          {/* Category */}
          {categoryScore && (
            <div className="col-md-4">
              <div className="p-2 border rounded bg-white h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small fw-semibold">Suggested Category</div>
                  <div className="d-flex align-items-center gap-2 mt-1">
                    <span className="fw-bold text-slate-800">{categoryScore.value}</span>
                    <span className="badge bg-success bg-opacity-10 text-success border border-success-subtle">
                      {Math.round(categoryScore.confidence * 100)}% Match
                    </span>
                  </div>
                </div>
                {onApplyCategory && (
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-primary mt-2 w-100"
                    onClick={() => onApplyCategory(categoryScore.value)}
                  >
                    <i className="bi bi-check2 me-1"></i>Apply Category
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Priority */}
          {priorityScore && (
            <div className="col-md-4">
              <div className="p-2 border rounded bg-white h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small fw-semibold">Suggested Priority</div>
                  <div className="d-flex align-items-center gap-2 mt-1">
                    <span className="fw-bold text-slate-800">{priorityScore.value}</span>
                    <span className="badge bg-warning bg-opacity-25 text-dark border border-warning-subtle">
                      {Math.round(priorityScore.confidence * 100)}% Confidence
                    </span>
                  </div>
                </div>
                {onApplyPriority && (
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-warning mt-2 w-100"
                    onClick={() => onApplyPriority(priorityScore.value)}
                  >
                    <i className="bi bi-check2 me-1"></i>Apply Priority
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Suggested Team */}
          {teamScore && (
            <div className="col-md-4">
              <div className="p-2 border rounded bg-white h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small fw-semibold">Recommended Team</div>
                  <div className="d-flex align-items-center gap-2 mt-1">
                    <span className="fw-bold text-slate-800">{teamScore.value.replace(/_/g, ' ')}</span>
                    <span className="badge bg-secondary bg-opacity-10 text-secondary border">
                      {Math.round(teamScore.confidence * 100)}%
                    </span>
                  </div>
                </div>
                <div className="small text-muted mt-2 fst-italic">
                  <i className="bi bi-arrow-right-short"></i>Auto-routing suggestion
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Normalized Summary */}
        {analysis.summary && (
          <div className="mb-3 p-2 bg-white rounded border">
            <div className="text-muted small fw-semibold mb-1">
              <i className="bi bi-file-text me-1"></i>Normalized Problem Statement:
            </div>
            <div className="small text-slate-800">{analysis.summary}</div>
          </div>
        )}

        {/* Prediction Reasons */}
        {reasons.length > 0 && (
          <div className="mb-3">
            <div className="text-muted small fw-semibold mb-1">
              <i className="bi bi-lightbulb me-1"></i>Explainable Prediction Signals:
            </div>
            <ul className="list-unstyled mb-0 small ps-2">
              {reasons.map((reason, idx) => (
                <li key={idx} className="text-slate-700 d-flex align-items-start gap-2 mb-1">
                  <i className="bi bi-check-circle-fill text-success fs-6 mt-1 flex-shrink-0"></i>
                  <span>{reason}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Apply All Button */}
        {onApplyAll && categoryScore && priorityScore && (
          <div className="d-flex justify-content-end gap-2 pt-2 border-top">
            <button
              type="button"
              className="btn btn-sm btn-primary"
              onClick={() => onApplyAll({
                category: categoryScore.value,
                priority: priorityScore.value,
              })}
            >
              <i className="bi bi-magic me-1"></i>Apply All AI Suggestions
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default AiTicketAnalysisCard;
