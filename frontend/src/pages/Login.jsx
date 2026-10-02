import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { extractErrorMessage } from '../services/api';
import ErrorAlert from '../components/ErrorAlert';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { login, sessionExpiredMessage, clearSessionMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = location.state?.from?.pathname || '/dashboard';

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please provide both email and password.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickFill = (roleEmail) => {
    setEmail(roleEmail);
    // Suggest the default test password from integration tests
    setPassword('TestPassOnlyInTests!2026');
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-light px-3 py-5">
      <div className="tc-card p-4 p-md-5 w-100" style={{ maxWidth: '440px' }}>
        <div className="text-center mb-4">
          <div className="d-inline-flex align-items-center justify-content-center bg-primary text-white rounded-3 p-3 mb-3">
            <i className="bi bi-cpu-fill fs-2"></i>
          </div>
          <h3 className="fw-bold text-slate-900 mb-1">TechConnect</h3>
          <p className="text-muted small">AI-Powered IT Service Management Platform</p>
        </div>

        {sessionExpiredMessage && (
          <div className="alert alert-warning alert-dismissible fade show small" role="alert">
            <i className="bi bi-clock-history me-2"></i>
            {sessionExpiredMessage}
            <button type="button" className="btn-close" onClick={clearSessionMessage}></button>
          </div>
        )}

        <ErrorAlert message={error} onDismiss={() => setError(null)} />

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label htmlFor="loginEmail" className="form-label small fw-semibold text-slate-700">Email Address</label>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-envelope"></i>
              </span>
              <input
                id="loginEmail"
                type="email"
                className="form-control"
                placeholder="name@techconnect.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </div>
          </div>

          <div className="mb-4">
            <div className="d-flex justify-content-between align-items-center mb-1">
              <label htmlFor="loginPassword" className="form-label small fw-semibold text-slate-700 mb-0">Password</label>
            </div>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-lock"></i>
              </span>
              <input
                id="loginPassword"
                type="password"
                className="form-control"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 py-2 fw-semibold"
            disabled={submitting}
          >
            {submitting ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                Signing In...
              </>
            ) : (
              'Sign In to TechConnect'
            )}
          </button>
        </form>

        <div className="text-center mt-4 pt-3 border-top">
          <p className="text-muted small mb-0">
            Don't have an account?{' '}
            <Link to="/register" className="text-primary text-decoration-none fw-semibold">
              Create employee account
            </Link>
          </p>
        </div>

        {/* Quick Credentials Demo Helper */}
        <div className="mt-4 p-3 bg-light rounded border text-start">
          <div className="small fw-semibold text-secondary mb-2 d-flex align-items-center gap-1">
            <i className="bi bi-info-circle"></i> Quick Test Accounts (Demo):
          </div>
          <div className="d-flex flex-wrap gap-1">
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => handleQuickFill('employee@techconnect.com')}
            >
              Employee
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => handleQuickFill('engineer@techconnect.com')}
            >
              Engineer
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => handleQuickFill('manager@techconnect.com')}
            >
              Manager
            </button>
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => handleQuickFill('admin@techconnect.com')}
            >
              Admin
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
