import React from 'react';

const CATEGORIES = [
  { value: 'ALL', label: 'All Categories' },
  { value: 'HARDWARE', label: 'Hardware' },
  { value: 'SOFTWARE', label: 'Software' },
  { value: 'NETWORK', label: 'Network' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'ACCESS_MANAGEMENT', label: 'Access Management' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'VPN', label: 'VPN' },
  { value: 'OTHER', label: 'Other' },
];

const CopilotInput = ({
  query,
  setQuery,
  category,
  setCategory,
  ticketContext,
  onClearTicketContext,
  onSubmit,
  loading,
}) => {
  const handleSubmit = (e) => {
    e.preventDefault();
    if (!query.trim() || loading) return;
    onSubmit();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (query.trim() && !loading) {
        onSubmit();
      }
    }
  };

  return (
    <div className="tc-card p-4 mb-4">
      {ticketContext && (
        <div className="alert alert-info py-2 px-3 mb-3 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <i className="bi bi-ticket-perforated-fill fs-5"></i>
            <div>
              <strong>Active Ticket Context: #{ticketContext.id}</strong> - {ticketContext.title}
              <span className="badge bg-light text-dark ms-2">{ticketContext.category || 'General'}</span>
            </div>
          </div>
          <button
            type="button"
            className="btn btn-sm btn-outline-secondary"
            onClick={onClearTicketContext}
            title="Remove ticket context"
          >
            <i className="bi bi-x"></i> Clear Context
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label htmlFor="copilotQueryInput" className="form-label fw-bold text-slate-800">
            Ask your IT support question:
          </label>
          <div className="input-group input-group-lg shadow-sm">
            <span className="input-group-text bg-white border-end-0 text-muted">
              <i className="bi bi-search"></i>
            </span>
            <input
              id="copilotQueryInput"
              type="text"
              className="form-control border-start-0"
              placeholder="e.g. My corporate VPN keeps disconnecting when I work from home..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
              maxLength={1000}
              autoComplete="off"
            />
            <button
              type="submit"
              className="btn btn-primary px-4 d-flex align-items-center gap-2"
              disabled={!query.trim() || loading}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span>Synthesizing...</span>
                </>
              ) : (
                <>
                  <i className="bi bi-robot"></i>
                  <span>Ask AI</span>
                </>
              )}
            </button>
          </div>
          <div className="d-flex justify-content-between align-items-center mt-2">
            <div className="d-flex align-items-center gap-2">
              <span className="text-muted small">Filter domain:</span>
              <select
                className="form-select form-select-sm w-auto"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                disabled={loading}
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat.value} value={cat.value}>
                    {cat.label}
                  </option>
                ))}
              </select>
            </div>
            <span className="text-muted small">Press Enter to submit</span>
          </div>
        </div>
      </form>
    </div>
  );
};

export default CopilotInput;
