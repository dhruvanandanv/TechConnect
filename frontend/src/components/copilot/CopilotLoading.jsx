import React from 'react';

const CopilotLoading = () => {
  return (
    <div className="tc-card p-5 mb-4 text-center">
      <div className="spinner-border text-primary mb-3" style={{ width: '3rem', height: '3rem' }} role="status">
        <span className="visually-hidden">Loading...</span>
      </div>
      <h5 className="fw-bold text-slate-800 mb-2">Formulating Grounded Response...</h5>
      <p className="text-muted small mb-0">
        Searching vector embeddings &bull; Extracting relevant knowledge chunks &bull; Synthesizing troubleshooting steps
      </p>
    </div>
  );
};

export default CopilotLoading;
