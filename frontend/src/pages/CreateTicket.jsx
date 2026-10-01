import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import ticketService from '../services/ticketService';
import { extractErrorMessage } from '../services/api';
import ErrorAlert from '../components/ErrorAlert';

const CATEGORIES = [
  { value: 'HARDWARE', label: 'Hardware (Laptops, Monitors, Peripherals)' },
  { value: 'SOFTWARE', label: 'Software (Operating Systems, Applications)' },
  { value: 'NETWORK', label: 'Network (Wi-Fi, Ethernet, Switches)' },
  { value: 'SECURITY', label: 'Security & Antivirus' },
  { value: 'ACCESS_MANAGEMENT', label: 'Access Management (Accounts, Permissions)' },
  { value: 'EMAIL', label: 'Email & Messaging' },
  { value: 'VPN', label: 'VPN & Remote Access' },
  { value: 'OTHER', label: 'Other Technical Inquiries' },
];

const PRIORITIES = [
  { value: 'LOW', label: 'Low — General questions, non-urgent minor requests (72h SLA)' },
  { value: 'MEDIUM', label: 'Medium — Normal operational inquiry or software glitch (48h SLA)' },
  { value: 'HIGH', label: 'High — Significant impairment of work productivity (24h SLA)' },
  { value: 'CRITICAL', label: 'Critical — Complete outage affecting operations (2h SLA)' },
];

const CreateTicket = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: 'HARDWARE',
    priority: 'MEDIUM',
  });

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const { title, description, category, priority } = formData;

    if (!title.trim() || title.trim().length < 3) {
      setError('Title must be at least 3 characters long.');
      return;
    }

    if (!description.trim() || description.trim().length < 5) {
      setError('Description must be at least 5 characters long.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);

      const createdTicket = await ticketService.createTicket({
        title: title.trim(),
        description: description.trim(),
        category,
        priority,
      });

      // Redirect directly to the newly created ticket's details page
      navigate(`/tickets/${createdTicket.id}`, {
        state: { successMessage: `Ticket #${createdTicket.id} was created successfully.` },
      });
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container-fluid p-0" style={{ maxWidth: '850px' }}>
      {/* Breadcrumb Navigation */}
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/tickets" className="text-decoration-none">Tickets</Link>
          </li>
          <li className="breadcrumb-item active" aria-current="page">New Ticket</li>
        </ol>
      </nav>

      <div className="tc-card">
        <div className="tc-card-header">
          <div>
            <h4 className="fw-bold text-slate-900 mb-1">Create IT Service Ticket</h4>
            <p className="text-muted small mb-0">
              Submit a technical support request to the IT Operations & Infrastructure team.
            </p>
          </div>
          <span className="badge bg-primary bg-opacity-10 text-primary border border-primary-subtle px-2 py-1">
            <i className="bi bi-shield-check me-1"></i>SLA Tracked
          </span>
        </div>

        <div className="tc-card-body p-4">
          <ErrorAlert message={error} onDismiss={() => setError(null)} />

          <form onSubmit={handleSubmit} noValidate>
            {/* Title */}
            <div className="mb-3">
              <label htmlFor="ticketTitle" className="form-label fw-semibold text-slate-700">
                Ticket Title <span className="text-danger">*</span>
              </label>
              <input
                id="ticketTitle"
                type="text"
                name="title"
                className="form-control"
                placeholder="e.g., Cannot connect to corporate VPN from remote office"
                value={formData.title}
                onChange={handleChange}
                maxLength={255}
                required
              />
              <div className="form-text small">
                Provide a concise summary of the issue (3 to 255 characters).
              </div>
            </div>

            {/* Category & Priority Grid */}
            <div className="row g-3 mb-3">
              <div className="col-12 col-md-6">
                <label htmlFor="ticketCategory" className="form-label fw-semibold text-slate-700">
                  Category <span className="text-danger">*</span>
                </label>
                <select
                  id="ticketCategory"
                  name="category"
                  className="form-select"
                  value={formData.category}
                  onChange={handleChange}
                  required
                >
                  {CATEGORIES.map((cat) => (
                    <option key={cat.value} value={cat.value}>
                      {cat.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="col-12 col-md-6">
                <label htmlFor="ticketPriority" className="form-label fw-semibold text-slate-700">
                  Priority <span className="text-danger">*</span>
                </label>
                <select
                  id="ticketPriority"
                  name="priority"
                  className="form-select"
                  value={formData.priority}
                  onChange={handleChange}
                  required
                >
                  {PRIORITIES.map((p) => (
                    <option key={p.value} value={p.value}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Detailed Description */}
            <div className="mb-4">
              <label htmlFor="ticketDescription" className="form-label fw-semibold text-slate-700">
                Detailed Description <span className="text-danger">*</span>
              </label>
              <textarea
                id="ticketDescription"
                name="description"
                rows="6"
                className="form-control"
                placeholder="Please describe the issue, symptoms, error codes, and steps to reproduce..."
                value={formData.description}
                onChange={handleChange}
                required
              ></textarea>
              <div className="form-text small">
                Include any error messages, device identifiers, or troubleshooting steps already attempted.
              </div>
            </div>

            {/* Form Action Buttons */}
            <div className="d-flex justify-content-end gap-2 pt-3 border-top">
              <Link to="/tickets" className="btn btn-outline-secondary px-4">
                Cancel
              </Link>
              <button
                type="submit"
                className="btn btn-primary px-4 fw-semibold d-flex align-items-center gap-2"
                disabled={submitting}
              >
                {submitting ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status"></span>
                    <span>Submitting Ticket...</span>
                  </>
                ) : (
                  <>
                    <i className="bi bi-send-fill"></i>
                    <span>Submit Ticket</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default CreateTicket;
