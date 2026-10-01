import React, { useState, useEffect, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import ticketService from '../services/ticketService';
import { extractErrorMessage } from '../services/api';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorAlert from '../components/ErrorAlert';
import { formatDate, formatEnum } from '../utils/formatters';

const TICKET_STATUSES = [
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'RESOLVED',
  'CLOSED',
  'ESCALATED',
  'MANAGER_REVIEW',
];

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const CATEGORIES = [
  'HARDWARE',
  'SOFTWARE',
  'NETWORK',
  'SECURITY',
  'ACCESS_MANAGEMENT',
  'EMAIL',
  'VPN',
  'OTHER',
];

const Tickets = ({ filterAssignedOnly = false }) => {
  const [searchParams, setSearchParams] = useSearchParams();

  // Filters from query params or state
  const [status, setStatus] = useState(searchParams.get('status') || '');
  const [priority, setPriority] = useState(searchParams.get('priority') || '');
  const [category, setCategory] = useState(searchParams.get('category') || '');
  const [searchQuery, setSearchQuery] = useState('');

  // Pagination state
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Data & UI states
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchTickets = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await ticketService.getMyTickets({
        status: status || undefined,
        priority: priority || undefined,
        category: category || undefined,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      });

      let content = response.content || [];

      // If viewing assigned tickets only (for engineer shortcuts)
      if (filterAssignedOnly) {
        content = content.filter((t) => t.assignedEngineerName);
      }

      setTickets(content);
      setTotalPages(response.totalPages || 0);
      setTotalElements(response.totalElements || 0);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [status, priority, category, page, pageSize, filterAssignedOnly]);

  useEffect(() => {
    fetchTickets();
  }, [fetchTickets]);

  const handleFilterChange = (filterType, value) => {
    setPage(0);
    if (filterType === 'status') setStatus(value);
    if (filterType === 'priority') setPriority(value);
    if (filterType === 'category') setCategory(value);

    const newParams = new URLSearchParams(searchParams);
    if (value) {
      newParams.set(filterType, value);
    } else {
      newParams.delete(filterType);
    }
    setSearchParams(newParams);
  };

  const handleResetFilters = () => {
    setStatus('');
    setPriority('');
    setCategory('');
    setSearchQuery('');
    setPage(0);
    setSearchParams(new URLSearchParams());
  };

  // Client-side search filtering across title and requester
  const filteredTickets = tickets.filter((t) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      t.title?.toLowerCase().includes(q) ||
      t.requesterName?.toLowerCase().includes(q) ||
      t.requesterEmail?.toLowerCase().includes(q) ||
      t.id?.toString().includes(q)
    );
  });

  return (
    <div className="container-fluid p-0">
      {/* Page Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h3 className="fw-bold text-slate-900 mb-1">
            {filterAssignedOnly ? 'My Assigned Tickets' : 'Ticket Management'}
          </h3>
          <p className="text-muted mb-0">
            {totalElements} total tickets found in your current view
          </p>
        </div>
        <Link to="/tickets/new" className="btn btn-primary d-flex align-items-center gap-2">
          <i className="bi bi-plus-lg"></i>
          <span>Create Ticket</span>
        </Link>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Filter and Search Bar */}
      <div className="tc-card p-3 mb-4">
        <div className="row g-2 align-items-center">
          <div className="col-12 col-md-4">
            <div className="input-group">
              <span className="input-group-text bg-white text-muted">
                <i className="bi bi-search"></i>
              </span>
              <input
                type="text"
                className="form-control"
                placeholder="Search by ID, title, or requester..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          <div className="col-6 col-md-2">
            <select
              className="form-select"
              value={status}
              onChange={(e) => handleFilterChange('status', e.target.value)}
              aria-label="Filter by Status"
            >
              <option value="">All Statuses</option>
              {TICKET_STATUSES.map((st) => (
                <option key={st} value={st}>
                  {formatEnum(st)}
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-2">
            <select
              className="form-select"
              value={priority}
              onChange={(e) => handleFilterChange('priority', e.target.value)}
              aria-label="Filter by Priority"
            >
              <option value="">All Priorities</option>
              {PRIORITIES.map((pr) => (
                <option key={pr} value={pr}>
                  {formatEnum(pr)}
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-2">
            <select
              className="form-select"
              value={category}
              onChange={(e) => handleFilterChange('category', e.target.value)}
              aria-label="Filter by Category"
            >
              <option value="">All Categories</option>
              {CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {formatEnum(cat)}
                </option>
              ))}
            </select>
          </div>

          <div className="col-6 col-md-2 d-flex gap-2">
            <button
              type="button"
              className="btn btn-outline-secondary w-100"
              onClick={handleResetFilters}
              title="Reset all filters"
            >
              <i className="bi bi-arrow-counterclockwise me-1"></i>Reset
            </button>
          </div>
        </div>
      </div>

      {/* Ticket List Table */}
      <div className="tc-card">
        <div className="tc-card-body p-0">
          {loading ? (
            <div className="py-5">
              <LoadingSpinner message="Fetching ticket records..." />
            </div>
          ) : filteredTickets.length === 0 ? (
            <div className="text-center py-5">
              <i className="bi bi-inbox text-muted fs-1 mb-2"></i>
              <h5 className="text-muted fw-bold">No tickets found</h5>
              <p className="small text-muted mb-3">
                {searchQuery || status || priority || category
                  ? 'No tickets match the selected filters.'
                  : 'Your ticket queue is currently empty.'}
              </p>
              {(searchQuery || status || priority || category) && (
                <button className="btn btn-sm btn-outline-primary" onClick={handleResetFilters}>
                  Clear Filters
                </button>
              )}
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
                    <th scope="col">Created Date</th>
                    <th scope="col" className="text-end pe-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map((ticket) => (
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
                          <span className="badge bg-light text-secondary border me-1">
                            {formatEnum(ticket.category)}
                          </span>
                          {ticket.departmentName && `• ${ticket.departmentName}`}
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
                          className="btn btn-sm btn-outline-primary py-1 px-3"
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Pagination Footer */}
        {totalPages > 1 && (
          <div className="tc-card-footer px-3 py-2 border-top d-flex justify-content-between align-items-center">
            <span className="small text-muted">
              Showing page {page + 1} of {totalPages} ({totalElements} items)
            </span>
            <nav aria-label="Ticket table pagination">
              <ul className="pagination pagination-sm mb-0">
                <li className={`page-item ${page === 0 ? 'disabled' : ''}`}>
                  <button
                    className="page-link"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                  >
                    Previous
                  </button>
                </li>
                {Array.from({ length: totalPages }).map((_, idx) => {
                  // Only display surrounding pages if many pages
                  if (idx === 0 || idx === totalPages - 1 || Math.abs(idx - page) <= 1) {
                    return (
                      <li
                        key={idx}
                        className={`page-item ${page === idx ? 'active' : ''}`}
                      >
                        <button className="page-link" onClick={() => setPage(idx)}>
                          {idx + 1}
                        </button>
                      </li>
                    );
                  }
                  if (idx === 1 && page > 2) {
                    return <li key={idx} className="page-item disabled"><span className="page-link">...</span></li>;
                  }
                  if (idx === totalPages - 2 && page < totalPages - 3) {
                    return <li key={idx} className="page-item disabled"><span className="page-link">...</span></li>;
                  }
                  return null;
                })}
                <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
                  <button
                    className="page-link"
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                  >
                    Next
                  </button>
                </li>
              </ul>
            </nav>
          </div>
        )}
      </div>
    </div>
  );
};

export default Tickets;
