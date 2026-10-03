import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { formatRole } from '../utils/formatters';

const Navbar = ({ onToggleSidebar }) => {
  const { currentUser, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="tc-navbar">
      <div className="d-flex align-items-center gap-3">
        {onToggleSidebar && (
          <button
            className="btn btn-sm btn-outline-secondary d-lg-none"
            onClick={onToggleSidebar}
            aria-label="Toggle Navigation"
          >
            <i className="bi bi-list fs-5"></i>
          </button>
        )}
        <Link to="/dashboard" className="tc-brand">
          <i className="bi bi-cpu-fill"></i>
          <span>TechConnect</span>
          <span className="badge-tag">ITSM</span>
        </Link>
      </div>

      {currentUser && (
        <div className="d-flex align-items-center gap-3">
          <div className="d-none d-sm-flex flex-column text-end">
            <span className="fw-semibold text-slate-800" style={{ fontSize: '0.9rem' }}>
              {currentUser.name}
            </span>
            <span className="text-muted" style={{ fontSize: '0.75rem' }}>
              {formatRole(currentUser.role)} {currentUser.departmentName ? `• ${currentUser.departmentName}` : ''}
            </span>
          </div>

          <Link
            to="/ai-support"
            className="btn btn-sm btn-outline-primary d-none d-md-flex align-items-center gap-1 py-1 px-2 rounded-pill"
            title="Open AI Support Copilot"
          >
            <i className="bi bi-stars"></i>
            <span>AI Copilot</span>
          </Link>

          <div className="dropdown">
            <button
              className="btn btn-outline-light border text-dark dropdown-toggle d-flex align-items-center gap-2 py-1 px-2"
              type="button"
              id="userMenuButton"
              data-bs-toggle="dropdown"
              aria-expanded="false"
              aria-label="User account menu"
              onClick={(e) => {
                const menu = document.getElementById('userDropdownMenu');
                if (menu) menu.classList.toggle('show');
              }}
            >
              <div
                className="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center"
                style={{ width: '32px', height: '32px', fontSize: '0.85rem', fontWeight: 600 }}
              >
                {currentUser.name ? currentUser.name.charAt(0).toUpperCase() : 'U'}
              </div>
            </button>
            <ul
              className="dropdown-menu dropdown-menu-end shadow-sm"
              id="userDropdownMenu"
              aria-labelledby="userMenuButton"
              style={{ minWidth: '200px' }}
            >
              <li className="px-3 py-2 border-bottom">
                <div className="fw-semibold">{currentUser.name}</div>
                <div className="small text-muted text-truncate">{currentUser.email}</div>
                <span className="badge bg-secondary mt-1">{formatRole(currentUser.role)}</span>
              </li>
              <li>
                <Link
                  className="dropdown-item py-2"
                  to="/profile"
                  onClick={() => document.getElementById('userDropdownMenu')?.classList.remove('show')}
                >
                  <i className="bi bi-person me-2"></i>My Profile
                </Link>
              </li>
              <li>
                <hr className="dropdown-divider my-1" />
              </li>
              <li>
                <button
                  className="dropdown-item py-2 text-danger"
                  onClick={() => {
                    document.getElementById('userDropdownMenu')?.classList.remove('show');
                    handleLogout();
                  }}
                >
                  <i className="bi bi-box-arrow-right me-2"></i>Log Out
                </button>
              </li>
            </ul>
          </div>
        </div>
      )}
    </header>
  );
};

export default Navbar;
