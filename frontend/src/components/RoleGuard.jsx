import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const RoleGuard = ({ allowedRoles = [], children }) => {
  const { currentUser } = useAuth();

  if (!currentUser) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(currentUser.role)) {
    return (
      <div className="container py-5 text-center">
        <div className="tc-card p-5 mx-auto" style={{ maxWidth: '550px' }}>
          <div className="text-danger mb-3">
            <i className="bi bi-shield-x fs-1"></i>
          </div>
          <h4 className="fw-bold text-slate-900">Access Denied</h4>
          <p className="text-muted mt-2">
            You do not have the required permissions ({allowedRoles.map(r => r.replace('ROLE_', '')).join(', ')}) to view this resource.
          </p>
          <div className="mt-4">
            <a href="/dashboard" className="btn btn-primary">
              <i className="bi bi-arrow-left me-2"></i>Return to Dashboard
            </a>
          </div>
        </div>
      </div>
    );
  }

  return children;
};

export default RoleGuard;
