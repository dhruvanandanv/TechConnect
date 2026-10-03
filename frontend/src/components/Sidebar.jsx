import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { formatRole } from '../utils/formatters';

const Sidebar = ({ isOpen, onCloseMobile }) => {
  const { currentUser, logout } = useAuth();

  if (!currentUser) return null;

  const role = currentUser.role;
  const isManagerOrAdmin = role === 'ROLE_MANAGER' || role === 'ROLE_ADMIN';
  const isEngineer = role === 'ROLE_ENGINEER';

  return (
    <aside className={`tc-sidebar ${isOpen ? 'show' : ''}`}>
      <nav className="tc-sidebar-nav">
        <div className="tc-nav-section-title">Core Workspace</div>

        <NavLink
          to="/dashboard"
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-speedometer2"></i>
          <span>Dashboard</span>
        </NavLink>

        <NavLink
          to="/tickets/new"
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-plus-circle"></i>
          <span>New Ticket</span>
        </NavLink>

        <NavLink
          to="/tickets"
          end
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-ticket-detailed"></i>
          <span>
            {role === 'ROLE_EMPLOYEE' ? 'My Tickets' : role === 'ROLE_ENGINEER' ? 'Ticket Queue' : 'All Tickets'}
          </span>
        </NavLink>

        {isEngineer && (
          <NavLink
            to="/assigned-tickets"
            className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
            onClick={onCloseMobile}
          >
            <i className="bi bi-person-lines-fill"></i>
            <span>My Assignments</span>
          </NavLink>
        )}

        {(isManagerOrAdmin || isEngineer) && (
          <>
            <div className="tc-nav-section-title mt-3">Governance & Analytics</div>
            {isManagerOrAdmin && (
              <NavLink
                to="/sla"
                className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
                onClick={onCloseMobile}
              >
                <i className="bi bi-clock-history"></i>
                <span>SLA Performance</span>
              </NavLink>
            )}
            <NavLink
              to="/analytics"
              className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
              onClick={onCloseMobile}
            >
              <i className="bi bi-graph-up-arrow"></i>
              <span>ITSM Analytics</span>
            </NavLink>
          </>
        )}

        <div className="tc-nav-section-title mt-3">Knowledge & SOPs</div>

        <NavLink
          to="/knowledge"
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-journal-bookmark"></i>
          <span>Knowledge Base</span>
        </NavLink>

        {(isEngineer || isManagerOrAdmin) && (
          <NavLink
            to="/knowledge/articles/new"
            className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
            onClick={onCloseMobile}
          >
            <i className="bi bi-pencil-square"></i>
            <span>New Article</span>
          </NavLink>
        )}

        <div className="tc-nav-section-title mt-3">AI Intelligence</div>

        <NavLink
          to="/ai-support"
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-robot"></i>
          <span>AI Support Copilot</span>
        </NavLink>

        <div className="tc-nav-section-title mt-3">User Account</div>

        <NavLink
          to="/profile"
          className={({ isActive }) => `tc-nav-link ${isActive ? 'active' : ''}`}
          onClick={onCloseMobile}
        >
          <i className="bi bi-person-badge"></i>
          <span>Profile</span>
        </NavLink>
      </nav>

      <div className="tc-sidebar-footer">
        <div className="d-flex align-items-center justify-content-between">
          <div className="text-truncate me-2" style={{ maxWidth: '160px' }}>
            <div className="text-white small fw-semibold text-truncate">{currentUser.name}</div>
            <div className="text-secondary" style={{ fontSize: '0.75rem' }}>{formatRole(role)}</div>
          </div>
          <button
            className="btn btn-sm btn-outline-secondary text-light border-0 p-1"
            title="Log Out"
            onClick={logout}
          >
            <i className="bi bi-box-arrow-right fs-5"></i>
          </button>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
