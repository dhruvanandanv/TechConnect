import React from 'react';
import { useAuth } from '../context/AuthContext';
import { formatRole, formatDate } from '../utils/formatters';

const Profile = () => {
  const { currentUser } = useAuth();

  if (!currentUser) return null;

  return (
    <div className="container-fluid p-0" style={{ maxWidth: '800px' }}>
      <div className="mb-4">
        <h3 className="fw-bold text-slate-900 mb-1">User Profile</h3>
        <p className="text-muted mb-0">Your TechConnect identity and organizational attributes</p>
      </div>

      <div className="tc-card mb-4">
        <div className="tc-card-body p-4">
          <div className="d-flex align-items-center gap-3 mb-4 pb-3 border-bottom">
            <div
              className="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center fw-bold fs-3"
              style={{ width: '64px', height: '64px' }}
            >
              {currentUser.name ? currentUser.name.charAt(0).toUpperCase() : 'U'}
            </div>
            <div>
              <h4 className="fw-bold mb-1">{currentUser.name}</h4>
              <div className="text-muted small">{currentUser.email}</div>
              <span className="badge bg-primary mt-2">{formatRole(currentUser.role)}</span>
            </div>
          </div>

          <div className="row g-4">
            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">Account User ID</span>
              <span className="fw-semibold">#{currentUser.id}</span>
            </div>

            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">System Role</span>
              <span className="fw-semibold">{formatRole(currentUser.role)}</span>
            </div>

            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">Department</span>
              <span className="fw-semibold">{currentUser.departmentName || 'Not Assigned'}</span>
            </div>

            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">Assigned Team</span>
              <span className="fw-semibold">{currentUser.teamName || 'General Queue'}</span>
            </div>

            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">Account Status</span>
              <span className="badge bg-success bg-opacity-10 text-success border border-success-subtle px-2 py-1">
                <i className="bi bi-check-circle-fill me-1"></i>Active
              </span>
            </div>

            <div className="col-12 col-sm-6">
              <span className="text-muted small d-block">Member Since</span>
              <span className="fw-semibold">{formatDate(currentUser.createdAt) || 'Active'}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Role Permissions Card */}
      <div className="tc-card">
        <div className="tc-card-header">
          <h6 className="mb-0 fw-semibold">Role Capabilities & Permissions</h6>
        </div>
        <div className="tc-card-body p-3">
          <ul className="list-group list-group-flush small">
            <li className="list-group-item d-flex align-items-center gap-2 px-0">
              <i className="bi bi-check2 text-success fs-5"></i>
              <span>Submit technical support service requests</span>
            </li>
            <li className="list-group-item d-flex align-items-center gap-2 px-0">
              <i className="bi bi-check2 text-success fs-5"></i>
              <span>Post comments and communicate on active tickets</span>
            </li>
            {currentUser.role !== 'ROLE_EMPLOYEE' && (
              <>
                <li className="list-group-item d-flex align-items-center gap-2 px-0">
                  <i className="bi bi-check2 text-success fs-5"></i>
                  <span>Transition ticket operational status (In Progress, Resolved, Escalated)</span>
                </li>
                <li className="list-group-item d-flex align-items-center gap-2 px-0">
                  <i className="bi bi-check2 text-success fs-5"></i>
                  <span>Post internal engineering collaboration notes</span>
                </li>
              </>
            )}
            {(currentUser.role === 'ROLE_MANAGER' || currentUser.role === 'ROLE_ADMIN') && (
              <>
                <li className="list-group-item d-flex align-items-center gap-2 px-0">
                  <i className="bi bi-check2 text-success fs-5"></i>
                  <span>Assign and reassign tickets across engineers and teams</span>
                </li>
                <li className="list-group-item d-flex align-items-center gap-2 px-0">
                  <i className="bi bi-check2 text-success fs-5"></i>
                  <span>Inspect SLA performance dashboard and breached ticket indicators</span>
                </li>
              </>
            )}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default Profile;
