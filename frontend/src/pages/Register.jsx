import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { extractErrorMessage } from '../services/api';
import ErrorAlert from '../components/ErrorAlert';

const Register = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { register } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const { name, email, password, confirmPassword } = formData;

    if (!name.trim() || !email.trim() || !password) {
      setError('Please fill in all required fields.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
      });

      setSuccessMessage('Registration successful! Redirecting to login...');
      setTimeout(() => {
        navigate('/login', {
          state: { registeredEmail: email.trim() },
        });
      }, 1500);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-light px-3 py-5">
      <div className="tc-card p-4 p-md-5 w-100" style={{ maxWidth: '480px' }}>
        <div className="text-center mb-4">
          <div className="d-inline-flex align-items-center justify-content-center bg-primary text-white rounded-3 p-3 mb-3">
            <i className="bi bi-person-plus-fill fs-2"></i>
          </div>
          <h3 className="fw-bold text-slate-900 mb-1">Create Account</h3>
          <p className="text-muted small">
            Standard Employee Self-Service Registration
          </p>
        </div>

        {successMessage && (
          <div className="alert alert-success d-flex align-items-center mb-3" role="alert">
            <i className="bi bi-check-circle-fill me-2 fs-5"></i>
            <div>{successMessage}</div>
          </div>
        )}

        <ErrorAlert message={error} onDismiss={() => setError(null)} />

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label className="form-label small fw-semibold text-slate-700">Full Name</label>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-person"></i>
              </span>
              <input
                type="text"
                name="name"
                className="form-control"
                placeholder="Jane Doe"
                value={formData.name}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="mb-3">
            <label className="form-label small fw-semibold text-slate-700">Corporate Email</label>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-envelope"></i>
              </span>
              <input
                type="email"
                name="email"
                className="form-control"
                placeholder="jane.doe@techconnect.com"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="mb-3">
            <label className="form-label small fw-semibold text-slate-700">Password</label>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-lock"></i>
              </span>
              <input
                type="password"
                name="password"
                className="form-control"
                placeholder="Minimum 8 characters"
                value={formData.password}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="mb-4">
            <label className="form-label small fw-semibold text-slate-700">Confirm Password</label>
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-shield-check"></i>
              </span>
              <input
                type="password"
                name="confirmPassword"
                className="form-control"
                placeholder="Re-enter password"
                value={formData.confirmPassword}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 py-2 fw-semibold"
            disabled={submitting || successMessage}
          >
            {submitting ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                Creating Account...
              </>
            ) : (
              'Register Employee Account'
            )}
          </button>
        </form>

        <div className="text-center mt-4 pt-3 border-top">
          <p className="text-muted small mb-0">
            Already have an account?{' '}
            <Link to="/login" className="text-primary text-decoration-none fw-semibold">
              Sign In
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;
