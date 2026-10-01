import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import slaService from '../services/slaService';
import { extractErrorMessage } from '../services/api';
import PriorityBadge from '../components/PriorityBadge';
import StatusBadge from '../components/StatusBadge';
import SlaBadge from '../components/SlaBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorAlert from '../components/ErrorAlert';
import { formatDate } from '../utils/formatters';

const SlaDashboard = () => {
  const [summary, setSummary] = useState(null);
  const [breachedTickets, setBreachedTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchSlaData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const [summaryRes, breachedRes] = await Promise.all([
        slaService.getSlaSummary(),
        slaService.getBreachedTickets(),
      ]);

      setSummary(summaryRes);
      setBreachedTickets(breachedRes);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSlaData();
  }, [fetchSlaData]);

  if (loading) {
    return <LoadingSpinner message="Calculating SLA compliance and breached ticket metrics..." />;
  }

  // Calculate compliance rate
  const totalEvaluated = (summary?.responseSlaMet || 0) + (summary?.responseSlaBreached || 0);
  const responseComplianceRate = totalEvaluated > 0
    ? Math.round(((summary?.responseSlaMet || 0) / totalEvaluated) * 100)
    : 100;

  const totalResolutionEvaluated = (summary?.resolutionSlaMet || 0) + (summary?.resolutionSlaBreached || 0);
  const resolutionComplianceRate = totalResolutionEvaluated > 0
    ? Math.round(((summary?.resolutionSlaMet || 0) / totalResolutionEvaluated) * 100)
    : 100;

  return (
    <div className="container-fluid p-0">
      {/* Page Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h3 className="fw-bold text-slate-900 mb-1">SLA Management & Compliance Engine</h3>
          <p className="text-muted mb-0">
            Real-time tracking of First Response and Resolution Service Level Agreements
          </p>
        </div>
        <button
          className="btn btn-outline-secondary d-flex align-items-center gap-2"
          onClick={fetchSlaData}
          title="Refresh SLA metrics"
        >
          <i className="bi bi-arrow-repeat"></i>
          <span>Refresh Metrics</span>
        </button>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Primary KPI Grid */}
      <div className="row g-3 mb-4">
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-primary bg-opacity-10 text-primary">
              <i className="bi bi-collection-fill"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Active Tickets</h6>
              <div className="stat-value">{summary?.totalActiveTickets ?? 0}</div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-success bg-opacity-10 text-success">
              <i className="bi bi-shield-check"></i>
            </div>
            <div className="tc-stat-content">
              <h6>On Track</h6>
              <div className="stat-value">{summary?.onTrack ?? 0}</div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-warning bg-opacity-10 text-warning">
              <i className="bi bi-shield-exclamation"></i>
            </div>
            <div className="tc-stat-content">
              <h6>At Risk (&le; 20% remaining)</h6>
              <div className="stat-value">{summary?.atRisk ?? 0}</div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl-3">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-danger bg-opacity-10 text-danger">
              <i className="bi bi-shield-x"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Breached Active</h6>
              <div className="stat-value">{summary?.breached ?? 0}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Compliance Target Breakdown Cards */}
      <div className="row g-4 mb-4">
        <div className="col-12 col-md-6">
          <div className="tc-card p-4">
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div>
                <h5 className="fw-semibold mb-0">Response SLA Compliance</h5>
                <span className="text-muted small">Target for initial technician response</span>
              </div>
              <span className={`badge fs-6 ${responseComplianceRate >= 90 ? 'bg-success' : 'bg-warning text-dark'}`}>
                {responseComplianceRate}% Compliance
              </span>
            </div>
            <div className="progress mb-3" style={{ height: '8px' }}>
              <div
                className="progress-bar bg-success"
                role="progressbar"
                style={{ width: `${responseComplianceRate}%` }}
                aria-valuenow={responseComplianceRate}
                aria-valuemin="0"
                aria-valuemax="100"
              ></div>
            </div>
            <div className="d-flex justify-content-between small text-muted">
              <span>Met On-Time: <strong className="text-success">{summary?.responseSlaMet ?? 0}</strong></span>
              <span>Breached: <strong className="text-danger">{summary?.responseSlaBreached ?? 0}</strong></span>
            </div>
          </div>
        </div>

        <div className="col-12 col-md-6">
          <div className="tc-card p-4">
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div>
                <h5 className="fw-semibold mb-0">Resolution SLA Compliance</h5>
                <span className="text-muted small">Target for complete ticket resolution</span>
              </div>
              <span className={`badge fs-6 ${resolutionComplianceRate >= 90 ? 'bg-success' : 'bg-danger'}`}>
                {resolutionComplianceRate}% Compliance
              </span>
            </div>
            <div className="progress mb-3" style={{ height: '8px' }}>
              <div
                className="progress-bar bg-primary"
                role="progressbar"
                style={{ width: `${resolutionComplianceRate}%` }}
                aria-valuenow={resolutionComplianceRate}
                aria-valuemin="0"
                aria-valuemax="100"
              ></div>
            </div>
            <div className="d-flex justify-content-between small text-muted">
              <span>Met On-Time: <strong className="text-success">{summary?.resolutionSlaMet ?? 0}</strong></span>
              <span>Breached: <strong className="text-danger">{summary?.resolutionSlaBreached ?? 0}</strong></span>
            </div>
          </div>
        </div>
      </div>

      {/* Breached Tickets Table */}
      <div className="tc-card">
        <div className="tc-card-header">
          <div className="d-flex align-items-center gap-2">
            <i className="bi bi-exclamation-triangle-fill text-danger"></i>
            <h5 className="mb-0 fw-semibold">Breached Tickets ({breachedTickets.length})</h5>
          </div>
          <span className="badge bg-danger bg-opacity-10 text-danger border border-danger-subtle">
            Immediate Action Required
          </span>
        </div>

        <div className="tc-card-body p-0">
          {breachedTickets.length === 0 ? (
            <div className="text-center py-5">
              <i className="bi bi-shield-check text-success fs-1 mb-2"></i>
              <h5 className="text-success fw-bold">Zero SLA Breaches</h5>
              <p className="text-muted small mb-0">
                All open tickets are currently progressing within their SLA service targets!
              </p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th scope="col" className="ps-3" style={{ width: '80px' }}>ID</th>
                    <th scope="col">Title</th>
                    <th scope="col">Priority</th>
                    <th scope="col">Status</th>
                    <th scope="col">Breach Type</th>
                    <th scope="col">Resolution Deadline</th>
                    <th scope="col">Breached At</th>
                    <th scope="col" className="text-end pe-3">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {breachedTickets.map((t) => (
                    <tr key={t.ticketId}>
                      <td className="ps-3 fw-bold text-danger">#{t.ticketId}</td>
                      <td>
                        <Link
                          to={`/tickets/${t.ticketId}`}
                          className="fw-semibold text-decoration-none text-slate-800"
                        >
                          {t.title}
                        </Link>
                      </td>
                      <td>
                        <PriorityBadge priority={t.priority} />
                      </td>
                      <td>
                        <StatusBadge status={t.status} />
                      </td>
                      <td>
                        <span className="badge bg-danger text-white">
                          {t.breachType || 'SLA BREACH'}
                        </span>
                      </td>
                      <td className="small text-muted">{formatDate(t.resolutionDeadline)}</td>
                      <td className="small text-danger fw-semibold">{formatDate(t.breachedAt)}</td>
                      <td className="text-end pe-3">
                        <Link
                          to={`/tickets/${t.ticketId}`}
                          className="btn btn-sm btn-outline-danger py-1 px-3"
                        >
                          Triage <i className="bi bi-arrow-right ms-1"></i>
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SlaDashboard;
