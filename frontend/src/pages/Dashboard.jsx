import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ticketService from '../services/ticketService';
import slaService from '../services/slaService';
import { extractErrorMessage } from '../services/api';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorAlert from '../components/ErrorAlert';
import { formatDate, formatRole, formatEnum } from '../utils/formatters';

const Dashboard = () => {
  const { currentUser } = useAuth();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Ticket data
  const [recentTickets, setRecentTickets] = useState([]);
  const [stats, setStats] = useState({
    total: 0,
    open: 0,
    inProgress: 0,
    waitingForUser: 0,
    resolved: 0,
    closed: 0,
    escalated: 0,
  });

  // SLA Summary data for Manager & Admin
  const [slaSummary, setSlaSummary] = useState(null);

  const role = currentUser?.role;
  const isManagerOrAdmin = role === 'ROLE_MANAGER' || role === 'ROLE_ADMIN';
  const isEngineer = role === 'ROLE_ENGINEER';

  const loadDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // 1. Fetch recent tickets from role-scoped /api/tickets/my
      const ticketPage = await ticketService.getMyTickets({
        page: 0,
        size: 50,
        sort: 'createdAt,desc',
      });

      const tickets = ticketPage.content || [];
      setRecentTickets(tickets.slice(0, 8));

      // Calculate status breakdown from tickets
      const newStats = {
        total: ticketPage.totalElements || tickets.length,
        open: tickets.filter((t) => t.status === 'OPEN').length,
        assigned: tickets.filter((t) => t.status === 'ASSIGNED').length,
        inProgress: tickets.filter((t) => t.status === 'IN_PROGRESS').length,
        waitingForUser: tickets.filter((t) => t.status === 'WAITING_FOR_USER').length,
        resolved: tickets.filter((t) => t.status === 'RESOLVED').length,
        closed: tickets.filter((t) => t.status === 'CLOSED').length,
        escalated: tickets.filter((t) => t.status === 'ESCALATED').length,
      };
      setStats(newStats);

      // 2. If Manager or Admin, fetch SLA summary KPIs
      if (isManagerOrAdmin) {
        try {
          const summary = await slaService.getSlaSummary();
          setSlaSummary(summary);
        } catch (slaErr) {
          console.warn('Could not load SLA summary:', slaErr);
        }
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [isManagerOrAdmin]);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  if (loading) {
    return <LoadingSpinner message="Loading dashboard metrics..." />;
  }

  return (
    <div className="container-fluid p-0">
      {/* Page Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h3 className="fw-bold text-slate-900 mb-1">
            Welcome back, {currentUser?.name}
          </h3>
          <p className="text-muted mb-0">
            {formatRole(role)} Workspace • {currentUser?.departmentName || 'IT Support'}
          </p>
        </div>
        <div className="d-flex gap-2">
          <Link to="/tickets/new" className="btn btn-primary d-flex align-items-center gap-2">
            <i className="bi bi-plus-lg"></i>
            <span>Create Ticket</span>
          </Link>
          {isManagerOrAdmin && (
            <Link to="/sla" className="btn btn-outline-secondary d-flex align-items-center gap-2">
              <i className="bi bi-clock-history"></i>
              <span>SLA Performance</span>
            </Link>
          )}
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Role-specific Metrics Cards */}
      <div className="row g-3 mb-4">
        {role === 'ROLE_EMPLOYEE' && (
          <>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-primary bg-opacity-10 text-primary">
                  <i className="bi bi-ticket-detailed"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>My Total Tickets</h6>
                  <div className="stat-value">{stats.total}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-info bg-opacity-10 text-info">
                  <i className="bi bi-record-circle"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Open / In Progress</h6>
                  <div className="stat-value">{stats.open + stats.inProgress + stats.assigned}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-warning bg-opacity-10 text-warning">
                  <i className="bi bi-pause-circle"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Awaiting Your Info</h6>
                  <div className="stat-value">{stats.waitingForUser}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-success bg-opacity-10 text-success">
                  <i className="bi bi-check2-circle"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Resolved / Closed</h6>
                  <div className="stat-value">{stats.resolved + stats.closed}</div>
                </div>
              </div>
            </div>
          </>
        )}

        {isEngineer && (
          <>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-primary bg-opacity-10 text-primary">
                  <i className="bi bi-inbox-fill"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Queue & Assigned</h6>
                  <div className="stat-value">{stats.total}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-info bg-opacity-10 text-info">
                  <i className="bi bi-gear-wide-connected"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>In Progress</h6>
                  <div className="stat-value">{stats.inProgress}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-warning bg-opacity-10 text-warning">
                  <i className="bi bi-hourglass-split"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Waiting on User</h6>
                  <div className="stat-value">{stats.waitingForUser}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-danger bg-opacity-10 text-danger">
                  <i className="bi bi-arrow-up-circle"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>Escalated</h6>
                  <div className="stat-value">{stats.escalated}</div>
                </div>
              </div>
            </div>
          </>
        )}

        {isManagerOrAdmin && (
          <>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-primary bg-opacity-10 text-primary">
                  <i className="bi bi-collection-fill"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>{role === 'ROLE_ADMIN' ? 'Total Active Tickets' : 'Team Active Tickets'}</h6>
                  <div className="stat-value">{slaSummary?.totalActiveTickets ?? stats.total}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-success bg-opacity-10 text-success">
                  <i className="bi bi-shield-check"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>SLA On Track</h6>
                  <div className="stat-value">{slaSummary?.onTrack ?? '—'}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-warning bg-opacity-10 text-warning">
                  <i className="bi bi-shield-exclamation"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>SLA At Risk (&lt;20%)</h6>
                  <div className="stat-value">{slaSummary?.atRisk ?? '—'}</div>
                </div>
              </div>
            </div>
            <div className="col-12 col-sm-6 col-xl-3">
              <div className="tc-stat-card">
                <div className="tc-stat-icon bg-danger bg-opacity-10 text-danger">
                  <i className="bi bi-shield-x"></i>
                </div>
                <div className="tc-stat-content">
                  <h6>SLA Breached</h6>
                  <div className="stat-value">{slaSummary?.breached ?? '—'}</div>
                </div>
              </div>
            </div>
          </>
        )}
      </div>

      {/* SLA Metric Highlights for Manager / Admin */}
      {isManagerOrAdmin && slaSummary && (
        <div className="row g-3 mb-4">
          <div className="col-12 col-md-6">
            <div className="tc-card p-3">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="small fw-semibold text-muted text-uppercase">Response SLA Compliance</span>
                <span className="badge bg-light text-dark border">First Response</span>
              </div>
              <div className="d-flex gap-4">
                <div>
                  <span className="text-muted small">Target Met:</span>{' '}
                  <strong className="text-success">{slaSummary.responseSlaMet}</strong>
                </div>
                <div>
                  <span className="text-muted small">Breached:</span>{' '}
                  <strong className="text-danger">{slaSummary.responseSlaBreached}</strong>
                </div>
              </div>
            </div>
          </div>
          <div className="col-12 col-md-6">
            <div className="tc-card p-3">
              <div className="d-flex justify-content-between align-items-center mb-2">
                <span className="small fw-semibold text-muted text-uppercase">Resolution SLA Compliance</span>
                <span className="badge bg-light text-dark border">Full Resolution</span>
              </div>
              <div className="d-flex gap-4">
                <div>
                  <span className="text-muted small">Target Met:</span>{' '}
                  <strong className="text-success">{slaSummary.resolutionSlaMet}</strong>
                </div>
                <div>
                  <span className="text-muted small">Breached:</span>{' '}
                  <strong className="text-danger">{slaSummary.resolutionSlaBreached}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Recent Tickets Section */}
      <div className="tc-card">
        <div className="tc-card-header">
          <div className="d-flex align-items-center gap-2">
            <i className="bi bi-clock-history text-muted"></i>
            <h5 className="mb-0 fw-semibold">Recent Tickets</h5>
          </div>
          <Link to="/tickets" className="btn btn-sm btn-outline-primary">
            View All Tickets <i className="bi bi-arrow-right ms-1"></i>
          </Link>
        </div>
        <div className="tc-card-body p-0">
          {recentTickets.length === 0 ? (
            <div className="text-center py-5">
              <i className="bi bi-inbox text-muted fs-1 mb-2"></i>
              <h6 className="text-muted">No tickets found</h6>
              <p className="small text-muted mb-3">You don't have any active tickets in this view.</p>
              <Link to="/tickets/new" className="btn btn-sm btn-primary">
                Create First Ticket
              </Link>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th scope="col" className="ps-3" style={{ width: '80px' }}>ID</th>
                    <th scope="col">Title & Category</th>
                    <th scope="col">Priority</th>
                    <th scope="col">Status</th>
                    <th scope="col">Requester</th>
                    <th scope="col">Assigned Engineer</th>
                    <th scope="col">Created</th>
                    <th scope="col" className="text-end pe-3">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {recentTickets.map((ticket) => (
                    <tr key={ticket.id}>
                      <td className="ps-3 fw-bold text-secondary">
                        #{ticket.id}
                      </td>
                      <td>
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="fw-semibold text-decoration-none text-slate-800 d-block"
                        >
                          {ticket.title}
                        </Link>
                        <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                          {formatEnum(ticket.category)}
                        </span>
                      </td>
                      <td>
                        <PriorityBadge priority={ticket.priority} />
                      </td>
                      <td>
                        <StatusBadge status={ticket.status} />
                      </td>
                      <td>
                        <div className="small fw-semibold">{ticket.requesterName}</div>
                        <div className="text-muted" style={{ fontSize: '0.75rem' }}>
                          {ticket.requesterEmail}
                        </div>
                      </td>
                      <td>
                        {ticket.assignedEngineerName ? (
                          <span className="small text-slate-800 fw-medium">
                            <i className="bi bi-person me-1 text-primary"></i>
                            {ticket.assignedEngineerName}
                          </span>
                        ) : (
                          <span className="badge bg-light text-muted border">Unassigned</span>
                        )}
                      </td>
                      <td className="small text-muted">
                        {formatDate(ticket.createdAt)}
                      </td>
                      <td className="text-end pe-3">
                        <Link
                          to={`/tickets/${ticket.id}`}
                          className="btn btn-sm btn-outline-secondary py-1 px-2"
                          title="View Details"
                        >
                          <i className="bi bi-chevron-right"></i>
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

export default Dashboard;
