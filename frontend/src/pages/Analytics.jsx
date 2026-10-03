import React, { useState, useEffect, useCallback } from 'react';
import analyticsService from '../services/analyticsService';
import { extractErrorMessage } from '../services/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorAlert from '../components/ErrorAlert';
import { formatEnum } from '../utils/formatters';

const Analytics = () => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('overview');

  const fetchAnalytics = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await analyticsService.getOverview();
      setData(res);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  if (loading) {
    return <LoadingSpinner message="Aggregating ITSM platform analytics..." />;
  }

  // Calculate percentages
  const totalTickets = data?.totalTickets || 0;
  const criticalCount = data?.priorityDistribution?.CRITICAL || 0;
  const highCount = data?.priorityDistribution?.HIGH || 0;
  const mediumCount = data?.priorityDistribution?.MEDIUM || 0;
  const lowCount = data?.priorityDistribution?.LOW || 0;

  return (
    <div className="container-fluid p-0">
      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h3 className="fw-bold text-slate-900 mb-1">ITSM Executive Analytics & Reporting</h3>
          <p className="text-muted mb-0">
            Real-time analytics engine tracking service desk performance, SLA governance, and workload distribution
          </p>
        </div>
        <button
          className="btn btn-outline-secondary d-flex align-items-center gap-2"
          onClick={fetchAnalytics}
          title="Refresh analytics data"
        >
          <i className="bi bi-arrow-repeat"></i>
          <span>Refresh Data</span>
        </button>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Top Level Metric Cards */}
      <div className="row g-3 mb-4">
        <div className="col-12 col-sm-6 col-xl">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-primary bg-opacity-10 text-primary">
              <i className="bi bi-ticket-perforated-fill"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Total Tickets</h6>
              <div className="stat-value">{totalTickets}</div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-info bg-opacity-10 text-info">
              <i className="bi bi-clock-history"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Open / Active</h6>
              <div className="stat-value">
                {(data?.openTickets || 0) + (data?.assignedTickets || 0) + (data?.inProgressTickets || 0)}
              </div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-success bg-opacity-10 text-success">
              <i className="bi bi-check-circle-fill"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Resolved / Closed</h6>
              <div className="stat-value">
                {(data?.resolvedTickets || 0) + (data?.closedTickets || 0)}
              </div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-warning bg-opacity-10 text-warning">
              <i className="bi bi-shield-check"></i>
            </div>
            <div className="tc-stat-content">
              <h6>SLA Compliance</h6>
              <div className="stat-value">{data?.slaCompliancePercentage ?? 100}%</div>
            </div>
          </div>
        </div>

        <div className="col-12 col-sm-6 col-xl">
          <div className="tc-stat-card">
            <div className="tc-stat-icon bg-secondary bg-opacity-10 text-secondary">
              <i className="bi bi-stopwatch-fill"></i>
            </div>
            <div className="tc-stat-content">
              <h6>Avg Resolution</h6>
              <div className="stat-value">
                {data?.averageResolutionTimeHours ? `${data.averageResolutionTimeHours} hrs` : 'N/A'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <ul className="nav nav-tabs mb-4 border-bottom">
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            <i className="bi bi-graph-up me-2"></i>Executive Overview
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'tickets' ? 'active' : ''}`}
            onClick={() => setActiveTab('tickets')}
          >
            <i className="bi bi-pie-chart me-2"></i>Ticket Distributions
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'sla' ? 'active' : ''}`}
            onClick={() => setActiveTab('sla')}
          >
            <i className="bi bi-shield-exclamation me-2"></i>SLA Governance
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'engineers' ? 'active' : ''}`}
            onClick={() => setActiveTab('engineers')}
          >
            <i className="bi bi-people me-2"></i>Engineer Workload
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link ${activeTab === 'knowledge' ? 'active' : ''}`}
            onClick={() => setActiveTab('knowledge')}
          >
            <i className="bi bi-journal-check me-2"></i>Knowledge & AI Metrics
          </button>
        </li>
      </ul>

      {/* Tab 1: Executive Overview */}
      {activeTab === 'overview' && (
        <div className="row g-4">
          <div className="col-12 col-lg-8">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">14-Day Activity Trend</h5>
              <div className="table-responsive">
                <table className="table table-sm align-middle mb-0">
                  <thead className="table-light">
                    <tr>
                      <th>Date</th>
                      <th className="text-center">Tickets Created</th>
                      <th className="text-center">Tickets Resolved</th>
                      <th>Activity Ratio</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(data?.ticketTrends || []).map((t) => (
                      <tr key={t.date}>
                        <td className="fw-semibold text-secondary">{t.date}</td>
                        <td className="text-center">
                          <span className="badge bg-primary bg-opacity-10 text-primary">
                            {t.createdCount}
                          </span>
                        </td>
                        <td className="text-center">
                          <span className="badge bg-success bg-opacity-10 text-success">
                            {t.resolvedCount}
                          </span>
                        </td>
                        <td>
                          <div className="progress" style={{ height: '6px' }}>
                            <div
                              className="progress-bar bg-primary"
                              role="progressbar"
                              style={{ width: `${Math.min(100, t.createdCount * 20)}%` }}
                            ></div>
                            <div
                              className="progress-bar bg-success"
                              role="progressbar"
                              style={{ width: `${Math.min(100, t.resolvedCount * 20)}%` }}
                            ></div>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div className="col-12 col-lg-4">
            <div className="tc-card p-4 mb-4">
              <h5 className="fw-bold mb-3">Priority Distribution</h5>
              <div className="mb-3">
                <div className="d-flex justify-content-between small mb-1">
                  <span className="text-danger fw-semibold">Critical ({criticalCount})</span>
                  <span>{totalTickets > 0 ? Math.round((criticalCount / totalTickets) * 100) : 0}%</span>
                </div>
                <div className="progress" style={{ height: '8px' }}>
                  <div
                    className="progress-bar bg-danger"
                    style={{ width: `${totalTickets > 0 ? (criticalCount / totalTickets) * 100 : 0}%` }}
                  ></div>
                </div>
              </div>

              <div className="mb-3">
                <div className="d-flex justify-content-between small mb-1">
                  <span className="text-warning fw-semibold">High ({highCount})</span>
                  <span>{totalTickets > 0 ? Math.round((highCount / totalTickets) * 100) : 0}%</span>
                </div>
                <div className="progress" style={{ height: '8px' }}>
                  <div
                    className="progress-bar bg-warning"
                    style={{ width: `${totalTickets > 0 ? (highCount / totalTickets) * 100 : 0}%` }}
                  ></div>
                </div>
              </div>

              <div className="mb-3">
                <div className="d-flex justify-content-between small mb-1">
                  <span className="text-primary fw-semibold">Medium ({mediumCount})</span>
                  <span>{totalTickets > 0 ? Math.round((mediumCount / totalTickets) * 100) : 0}%</span>
                </div>
                <div className="progress" style={{ height: '8px' }}>
                  <div
                    className="progress-bar bg-primary"
                    style={{ width: `${totalTickets > 0 ? (mediumCount / totalTickets) * 100 : 0}%` }}
                  ></div>
                </div>
              </div>

              <div>
                <div className="d-flex justify-content-between small mb-1">
                  <span className="text-secondary fw-semibold">Low ({lowCount})</span>
                  <span>{totalTickets > 0 ? Math.round((lowCount / totalTickets) * 100) : 0}%</span>
                </div>
                <div className="progress" style={{ height: '8px' }}>
                  <div
                    className="progress-bar bg-secondary"
                    style={{ width: `${totalTickets > 0 ? (lowCount / totalTickets) * 100 : 0}%` }}
                  ></div>
                </div>
              </div>
            </div>

            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">Resolution Velocity</h5>
              <div className="text-center py-3">
                <div className="fs-1 fw-bold text-primary mb-1">
                  {data?.averageResolutionTimeHours ?? 0} <span className="fs-6 text-muted">hours</span>
                </div>
                <p className="small text-muted mb-0">
                  Based on {data?.totalResolvedTicketsEvaluated || 0} fully resolved incidents
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Ticket & Category Distributions */}
      {activeTab === 'tickets' && (
        <div className="row g-4">
          <div className="col-12 col-md-6">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">Incidents by Category</h5>
              {Object.entries(data?.categoryDistribution || {}).map(([cat, count]) => (
                <div key={cat} className="mb-3">
                  <div className="d-flex justify-content-between small mb-1">
                    <span className="fw-semibold text-slate-800">{formatEnum(cat)}</span>
                    <span className="badge bg-light text-dark border">{count}</span>
                  </div>
                  <div className="progress" style={{ height: '6px' }}>
                    <div
                      className="progress-bar bg-info"
                      style={{ width: `${totalTickets > 0 ? (count / totalTickets) * 100 : 0}%` }}
                    ></div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">Status Breakdown</h5>
              {Object.entries(data?.statusDistribution || {}).map(([st, count]) => (
                <div key={st} className="mb-3">
                  <div className="d-flex justify-content-between small mb-1">
                    <span className="fw-semibold text-slate-800">{formatEnum(st)}</span>
                    <span className="badge bg-light text-dark border">{count}</span>
                  </div>
                  <div className="progress" style={{ height: '6px' }}>
                    <div
                      className="progress-bar bg-primary"
                      style={{ width: `${totalTickets > 0 ? (count / totalTickets) * 100 : 0}%` }}
                    ></div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: SLA Governance */}
      {activeTab === 'sla' && (
        <div className="row g-4">
          <div className="col-12 col-md-4">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">On Track</h6>
              <div className="fs-1 fw-bold text-success mb-1">{data?.slaOnTrackCount ?? 0}</div>
              <p className="small text-muted mb-0">Active tickets comfortably within SLA window</p>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">At Risk (&lt;20% Time)</h6>
              <div className="fs-1 fw-bold text-warning mb-1">{data?.slaAtRiskCount ?? 0}</div>
              <p className="small text-muted mb-0">Critical attention required before breach</p>
            </div>
          </div>

          <div className="col-12 col-md-4">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">Breached</h6>
              <div className="fs-1 fw-bold text-danger mb-1">{data?.slaBreachedCount ?? 0}</div>
              <p className="small text-muted mb-0">Violations requiring escalation & post-mortem</p>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">Response SLA Performance</h5>
              <div className="d-flex justify-content-between mb-2">
                <span>First Response Met:</span>
                <strong className="text-success">{data?.responseSlaMetCount ?? 0}</strong>
              </div>
              <div className="d-flex justify-content-between mb-2">
                <span>First Response Breached:</span>
                <strong className="text-danger">{data?.responseSlaBreachedCount ?? 0}</strong>
              </div>
            </div>
          </div>

          <div className="col-12 col-md-6">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-3">Resolution SLA Performance</h5>
              <div className="d-flex justify-content-between mb-2">
                <span>Resolution Met:</span>
                <strong className="text-success">{data?.resolutionSlaMetCount ?? 0}</strong>
              </div>
              <div className="d-flex justify-content-between mb-2">
                <span>Resolution Breached:</span>
                <strong className="text-danger">{data?.resolutionSlaBreachedCount ?? 0}</strong>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Engineer Workload */}
      {activeTab === 'engineers' && (
        <div className="tc-card">
          <div className="tc-card-header">
            <h5 className="mb-0 fw-semibold">Engineer Workload & Performance Scorecard</h5>
          </div>
          <div className="tc-card-body p-0">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th className="ps-3">Engineer Name</th>
                    <th>Email Address</th>
                    <th className="text-center">Active Queue</th>
                    <th className="text-center">Resolved Incidents</th>
                    <th className="text-end pe-3">Capacity Status</th>
                  </tr>
                </thead>
                <tbody>
                  {(data?.engineerWorkloads || []).length === 0 ? (
                    <tr>
                      <td colSpan="5" className="text-center py-4 text-muted">
                        No support engineers registered.
                      </td>
                    </tr>
                  ) : (
                    (data?.engineerWorkloads || []).map((eng) => (
                      <tr key={eng.engineerId}>
                        <td className="ps-3 fw-bold text-slate-800">
                          <i className="bi bi-person-fill text-primary me-2"></i>
                          {eng.name}
                        </td>
                        <td className="text-muted small">{eng.email}</td>
                        <td className="text-center">
                          <span className={`badge ${eng.activeTicketsCount > 5 ? 'bg-danger' : 'bg-primary'}`}>
                            {eng.activeTicketsCount}
                          </span>
                        </td>
                        <td className="text-center">
                          <span className="badge bg-success bg-opacity-10 text-success">
                            {eng.resolvedTicketsCount}
                          </span>
                        </td>
                        <td className="text-end pe-3">
                          {eng.activeTicketsCount > 5 ? (
                            <span className="badge bg-danger bg-opacity-10 text-danger border border-danger-subtle">
                              Overloaded
                            </span>
                          ) : eng.activeTicketsCount > 0 ? (
                            <span className="badge bg-success bg-opacity-10 text-success border border-success-subtle">
                              Active
                            </span>
                          ) : (
                            <span className="badge bg-secondary bg-opacity-10 text-secondary border border-secondary-subtle">
                              Available
                            </span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Tab 5: Knowledge & AI Metrics */}
      {activeTab === 'knowledge' && (
        <div className="row g-4">
          <div className="col-12 col-md-6 col-lg-3">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">Total Articles</h6>
              <div className="fs-1 fw-bold text-primary mb-1">{data?.totalKnowledgeArticles ?? 0}</div>
              <p className="small text-muted mb-0">Total documentation corpus</p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">Published SOPs</h6>
              <div className="fs-1 fw-bold text-success mb-1">{data?.publishedKnowledgeArticles ?? 0}</div>
              <p className="small text-muted mb-0">Authoritative articles for RAG</p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">Article Views</h6>
              <div className="fs-1 fw-bold text-info mb-1">{data?.totalKnowledgeArticleViews ?? 0}</div>
              <p className="small text-muted mb-0">Total readership across portal</p>
            </div>
          </div>

          <div className="col-12 col-md-6 col-lg-3">
            <div className="tc-card p-4 text-center">
              <h6 className="text-muted text-uppercase mb-2">Helpful Votes</h6>
              <div className="fs-1 fw-bold text-warning mb-1">{data?.totalKnowledgeArticleHelpfulVotes ?? 0}</div>
              <p className="small text-muted mb-0">Positive user feedback ratings</p>
            </div>
          </div>

          <div className="col-12">
            <div className="tc-card p-4">
              <h5 className="fw-bold mb-2">AI Copilot & Resolution Assistant Grounding Performance</h5>
              <p className="text-muted small mb-3">
                All AI triage, RAG support answers, and engineer resolution proposals are grounded strictly in the {data?.publishedKnowledgeArticles ?? 0} published articles and historical resolved tickets using SentenceTransformers (`all-MiniLM-L6-v2`) and pgvector HNSW indexing.
              </p>
              <div className="alert alert-info d-flex align-items-center mb-0">
                <i className="bi bi-info-circle-fill fs-4 me-3"></i>
                <div>
                  <strong>Enterprise Grounding Invariant:</strong> Zero hallucination tolerance. If semantic cosine similarity falls below the 0.30 confidence threshold, the AI gracefully declines to answer without guessing.
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Analytics;
