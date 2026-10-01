import React from 'react';
import { Link } from 'react-router-dom';

const NotFound = () => {
  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-light px-3 py-5">
      <div className="tc-card p-5 text-center" style={{ maxWidth: '500px' }}>
        <div className="text-primary mb-3">
          <i className="bi bi-compass fs-1"></i>
        </div>
        <h1 className="fw-bold text-slate-900 display-4">404</h1>
        <h4 className="fw-semibold mb-2">Page Not Found</h4>
        <p className="text-muted small mb-4">
          The page you are looking for does not exist, has been moved, or requires elevated privileges.
        </p>
        <Link to="/dashboard" className="btn btn-primary">
          <i className="bi bi-house-door me-2"></i>Back to Dashboard
        </Link>
      </div>
    </div>
  );
};

export default NotFound;
