import React from 'react';
import { Link } from 'react-router-dom';

const SUGGESTIONS = [
  {
    icon: 'bi-shield-lock',
    text: 'My corporate VPN keeps disconnecting when I work from home',
    category: 'VPN',
  },
  {
    icon: 'bi-phone',
    text: 'How do I reset my MFA authenticator after getting a new phone?',
    category: 'ACCESS_MANAGEMENT',
  },
  {
    icon: 'bi-envelope',
    text: 'Outlook mailbox is full and incoming emails are bouncing',
    category: 'SOFTWARE',
  },
  {
    icon: 'bi-printer',
    text: 'Network printer is offline and not responding to print jobs',
    category: 'HARDWARE',
  },
];

const CopilotEmptyState = ({ noSources, onSelectSuggestion }) => {
  if (noSources) {
    return (
      <div className="tc-card p-5 mb-4 text-center">
        <div className="text-warning mb-3">
          <i className="bi bi-journal-x" style={{ fontSize: '3.5rem' }}></i>
        </div>
        <h4 className="fw-bold text-slate-800 mb-2">No Matching Knowledge Found</h4>
        <p className="text-muted mb-4 max-w-lg mx-auto">
          I couldn't find a sufficiently relevant knowledge-base article for this question.
          TechConnect strictly avoids hallucinating advice not backed by verified documentation.
        </p>
        <div className="d-flex justify-content-center gap-2">
          <Link to="/knowledge" className="btn btn-outline-primary d-inline-flex align-items-center gap-2">
            <i className="bi bi-search"></i>
            <span>Search Knowledge Base</span>
          </Link>
          <Link to="/tickets/new" className="btn btn-primary d-inline-flex align-items-center gap-2">
            <i className="bi bi-plus-circle"></i>
            <span>Submit IT Support Ticket</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="tc-card p-4 mb-4">
      <div className="text-center mb-4">
        <div className="bg-primary-subtle text-primary rounded-circle d-inline-flex align-items-center justify-content-center mb-3" style={{ width: '60px', height: '60px' }}>
          <i className="bi bi-robot fs-2"></i>
        </div>
        <h4 className="fw-bold text-slate-900 mb-1">Welcome to TechConnect AI Support Copilot</h4>
        <p className="text-muted small mb-0">
          Ask questions in plain English. Responses are synthesized and grounded exclusively in verified IT Knowledge Base articles.
        </p>
      </div>

      <div className="row g-3">
        {SUGGESTIONS.map((s, idx) => (
          <div key={idx} className="col-12 col-md-6">
            <button
              type="button"
              className="btn btn-outline-light text-start text-dark w-100 p-3 h-100 border shadow-sm d-flex align-items-start gap-3 hover-shadow transition"
              onClick={() => onSelectSuggestion(s.text, s.category)}
            >
              <div className="bg-light text-primary rounded p-2 d-flex align-items-center justify-content-center">
                <i className={`bi ${s.icon} fs-5`}></i>
              </div>
              <div>
                <span className="badge bg-light text-secondary border mb-1">{s.category}</span>
                <div className="small fw-semibold text-slate-800">{s.text}</div>
              </div>
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default CopilotEmptyState;
