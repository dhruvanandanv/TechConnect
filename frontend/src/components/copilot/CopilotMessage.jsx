import React, { useState } from 'react';

const CopilotMessage = ({ response }) => {
  const [copied, setCopied] = useState(false);

  if (!response) return null;

  const handleCopy = () => {
    if (response.answer) {
      navigator.clipboard.writeText(response.answer);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const isGrounded = response.grounded;

  return (
    <div className="tc-card p-4 mb-4 border-start border-4 border-primary">
      {/* Header Attribution */}
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3 pb-2 border-bottom">
        <div className="d-flex align-items-center gap-2">
          <div className="bg-primary text-white rounded-circle p-2 d-flex align-items-center justify-content-center" style={{ width: '36px', height: '36px' }}>
            <i className="bi bi-robot fs-5"></i>
          </div>
          <div>
            <h5 className="fw-bold mb-0 text-slate-900">AI Support Copilot</h5>
            <span className="text-muted small">
              {isGrounded
                ? 'AI-generated from TechConnect knowledge-base sources.'
                : 'Direct Advisory Guidance'}
            </span>
          </div>
        </div>

        <div className="d-flex align-items-center gap-2">
          {response.processingTimeMs > 0 && (
            <span className="badge bg-light text-muted border">
              <i className="bi bi-stopwatch me-1"></i>
              {response.processingTimeMs}ms
            </span>
          )}
          {response.confidence !== null && response.confidence !== undefined && (
            <span className="badge bg-light text-success border">
              <i className="bi bi-check2-circle me-1"></i>
              Relevance: {Math.round(response.confidence * 100)}%
            </span>
          )}
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
            onClick={handleCopy}
            title="Copy answer to clipboard"
          >
            <i className={`bi ${copied ? 'bi-check2' : 'bi-clipboard'}`}></i>
            <span>{copied ? 'Copied' : 'Copy'}</span>
          </button>
        </div>
      </div>

      {/* Answer Body */}
      <div className="copilot-answer-body mb-3 text-slate-800" style={{ whiteSpace: 'pre-line', lineHeight: '1.7' }}>
        {response.answer}
      </div>

      {/* Security Disclaimer Notice */}
      <div className="alert alert-light border d-flex align-items-center gap-2 py-2 px-3 mb-0 small text-muted">
        <i className="bi bi-shield-check text-primary fs-5"></i>
        <span>
          <strong>Security Notice:</strong> Copilot provides recommendations from the TechConnect knowledge base. Verify critical actions with IT support.
        </span>
      </div>
    </div>
  );
};

export default CopilotMessage;
