import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ticketService from '../services/ticketService';
import slaService from '../services/slaService';
import { extractErrorMessage } from '../services/api';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import SlaBadge from '../components/SlaBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorAlert from '../components/ErrorAlert';
import { formatDate, formatDurationMinutes, formatEnum, formatRole } from '../utils/formatters';

const TicketDetails = () => {
  const { id } = useParams();
  const location = useLocation();
  const { currentUser } = useAuth();

  // Core Data State
  const [ticket, setTicket] = useState(null);
  const [slaData, setSlaData] = useState(null);
  const [comments, setComments] = useState([]);
  const [history, setHistory] = useState([]);
  const [assignments, setAssignments] = useState([]);

  // UI States
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(location.state?.successMessage || null);
  const [activeTab, setActiveTab] = useState('overview');

  // Comment submission
  const [newComment, setNewComment] = useState('');
  const [isInternalComment, setIsInternalComment] = useState(false);
  const [commentSubmitting, setCommentSubmitting] = useState(false);

  // Status Change Modal State
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [statusForm, setStatusForm] = useState({
    status: '',
    reason: '',
    resolutionDescription: '',
  });
  const [statusSubmitting, setStatusSubmitting] = useState(false);

  // Assignment Modal State
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [assignForm, setAssignForm] = useState({
    engineerId: '',
    teamId: '',
    notes: '',
  });
  const [assignSubmitting, setAssignSubmitting] = useState(false);
  const [selfAssignSubmitting, setSelfAssignSubmitting] = useState(false);

  const role = currentUser?.role;
  const isEmployee = role === 'ROLE_EMPLOYEE';
  const isEngineer = role === 'ROLE_ENGINEER';
  const isManager = role === 'ROLE_MANAGER';
  const isAdmin = role === 'ROLE_ADMIN';
  const isStaff = isEngineer || isManager || isAdmin;

  // Permitted transitions strictly matching the backend ITIL state machine
  const getStatusTransitionLabel = (st) => {
    switch (st) {
      case 'OPEN':
        return 'OPEN (Return to Queue)';
      case 'ASSIGNED':
        return 'ASSIGNED (Assignee Triage)';
      case 'IN_PROGRESS':
        return 'IN_PROGRESS (Active Work)';
      case 'WAITING_FOR_USER':
        return 'WAITING_FOR_USER (Awaiting Clarification)';
      case 'RESOLVED':
        return 'RESOLVED (Issue Solved)';
      case 'ESCALATED':
        return 'ESCALATED (High-tier Attention)';
      case 'MANAGER_REVIEW':
        return 'MANAGER_REVIEW (Manager Review & Governance)';
      case 'CLOSED':
        return 'CLOSED (Finalize Ticket)';
      default:
        return st;
    }
  };

  const getAllowedStatusTransitions = () => {
    if (!ticket) return [];
    const current = ticket.status;

    const STATE_TRANSITIONS = {
      OPEN: ['ASSIGNED'],
      ASSIGNED: ['IN_PROGRESS', 'OPEN'],
      IN_PROGRESS: ['WAITING_FOR_USER', 'RESOLVED', 'ESCALATED'],
      WAITING_FOR_USER: ['IN_PROGRESS', 'RESOLVED'],
      ESCALATED: ['MANAGER_REVIEW', 'IN_PROGRESS'],
      MANAGER_REVIEW: ['IN_PROGRESS', 'ASSIGNED', 'RESOLVED', 'CLOSED'],
      RESOLVED: ['CLOSED', 'IN_PROGRESS'],
      CLOSED: [],
    };

    const rawTargets = STATE_TRANSITIONS[current] || [];

    if (isEmployee) {
      if (current === 'RESOLVED') return [{ value: 'CLOSED', label: 'CLOSED (Confirm Resolution)' }];
      if (current === 'WAITING_FOR_USER') return [{ value: 'IN_PROGRESS', label: 'IN_PROGRESS (Resume Work / Clarification Provided)' }];
      return [];
    }

    if (isEngineer) {
      return rawTargets
        .filter((st) => st !== 'MANAGER_REVIEW')
        .map((st) => ({
          value: st,
          label: getStatusTransitionLabel(st),
        }));
    }

    if (isManager || isAdmin) {
      return rawTargets.map((st) => ({
        value: st,
        label: getStatusTransitionLabel(st),
      }));
    }

    return [];
  };

  const availableTransitions = getAllowedStatusTransitions();

  const loadTicketData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // Load Ticket Details
      const ticketData = await ticketService.getTicketById(id);
      setTicket(ticketData);

      // Concurrently load SLA, Comments, History, and Assignments
      const [slaRes, commentsRes, historyRes, assignmentsRes] = await Promise.allSettled([
        slaService.getTicketSla(id),
        ticketService.getComments(id),
        ticketService.getStatusHistory(id),
        ticketService.getAssignmentHistory(id),
      ]);

      if (slaRes.status === 'fulfilled') setSlaData(slaRes.value);
      if (commentsRes.status === 'fulfilled') setComments(commentsRes.value);
      if (historyRes.status === 'fulfilled') setHistory(historyRes.value);
      if (assignmentsRes.status === 'fulfilled') setAssignments(assignmentsRes.value);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadTicketData();
  }, [loadTicketData]);

  // Handle Comment Submission
  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!newComment.trim() || commentSubmitting) return;

    try {
      setCommentSubmitting(true);
      setError(null);
      const added = await ticketService.addComment(id, {
        content: newComment.trim(),
        isInternal: isStaff && isInternalComment,
      });

      setComments((prev) => [added, ...prev]);
      setNewComment('');
      setIsInternalComment(false);
      setSuccessMessage('Comment added successfully.');

      // Refresh SLA because staff comment records first response milestone
      if (isStaff && !ticket.respondedAt) {
        const slaRes = await slaService.getTicketSla(id).catch(() => null);
        if (slaRes) setSlaData(slaRes);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setCommentSubmitting(false);
    }
  };

  // Handle Status Update Transition
  const handleUpdateStatus = async (e) => {
    e.preventDefault();
    if (!statusForm.status || statusSubmitting) {
      setError('Please select a valid new status.');
      return;
    }

    try {
      setStatusSubmitting(true);
      setError(null);

      const updated = await ticketService.updateTicketStatus(id, {
        status: statusForm.status,
        reason: statusForm.reason.trim(),
        resolutionDescription:
          statusForm.status === 'RESOLVED' ? statusForm.resolutionDescription.trim() : undefined,
      });

      setTicket(updated);
      setShowStatusModal(false);
      setStatusForm({ status: '', reason: '', resolutionDescription: '' });
      setSuccessMessage(`Ticket status successfully updated to ${formatEnum(updated.status)}.`);

      // Refresh SLA and History
      const [slaRes, historyRes] = await Promise.allSettled([
        slaService.getTicketSla(id),
        ticketService.getStatusHistory(id),
      ]);
      if (slaRes.status === 'fulfilled') setSlaData(slaRes.value);
      if (historyRes.status === 'fulfilled') setHistory(historyRes.value);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setStatusSubmitting(false);
    }
  };

  // Handle Engineer Self-Assignment
  const handleSelfAssign = async () => {
    if (selfAssignSubmitting) return;
    try {
      setSelfAssignSubmitting(true);
      setError(null);
      const updated = await ticketService.assignTicket(id, {
        engineerId: currentUser.id,
        notes: 'Engineer self-assigned ticket from queue.',
      });
      setTicket(updated);
      setSuccessMessage('You have successfully assigned this ticket to yourself.');
      const [assignRes, historyRes, slaRes] = await Promise.allSettled([
        ticketService.getAssignmentHistory(id),
        ticketService.getStatusHistory(id),
        slaService.getTicketSla(id),
      ]);
      if (assignRes.status === 'fulfilled') setAssignments(assignRes.value);
      if (historyRes.status === 'fulfilled') setHistory(historyRes.value);
      if (slaRes.status === 'fulfilled') setSlaData(slaRes.value);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSelfAssignSubmitting(false);
    }
  };

  // Handle Manager / Admin Assignment
  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    if (!assignForm.engineerId || assignSubmitting) {
      setError('Please provide a valid Engineer ID.');
      return;
    }

    try {
      setAssignSubmitting(true);
      setError(null);
      const updated = await ticketService.assignTicket(id, {
        engineerId: Number(assignForm.engineerId),
        teamId: assignForm.teamId ? Number(assignForm.teamId) : undefined,
        notes: assignForm.notes.trim() || undefined,
      });
      setTicket(updated);
      setShowAssignModal(false);
      setAssignForm({ engineerId: '', teamId: '', notes: '' });
      setSuccessMessage('Ticket assignment updated successfully.');
      const [assignRes, historyRes, slaRes] = await Promise.allSettled([
        ticketService.getAssignmentHistory(id),
        ticketService.getStatusHistory(id),
        slaService.getTicketSla(id),
      ]);
      if (assignRes.status === 'fulfilled') setAssignments(assignRes.value);
      if (historyRes.status === 'fulfilled') setHistory(historyRes.value);
      if (slaRes.status === 'fulfilled') setSlaData(slaRes.value);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setAssignSubmitting(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Retrieving ticket records and SLA metrics..." />;
  }

  if (!ticket) {
    return (
      <div className="container py-5 text-center">
        <div className="tc-card p-5 mx-auto" style={{ maxWidth: '500px' }}>
          {error ? (
            <>
              <i className="bi bi-exclamation-triangle text-danger fs-1 mb-2"></i>
              <h4 className="fw-bold text-slate-900">Request Failed</h4>
              <p className="text-muted small">{error}</p>
            </>
          ) : (
            <>
              <i className="bi bi-question-circle text-muted fs-1 mb-2"></i>
              <h4 className="fw-bold text-slate-900">Ticket Not Found</h4>
              <p className="text-muted small">
                The requested ticket #{id} does not exist or you do not have permission to view it.
              </p>
            </>
          )}
          <Link to="/tickets" className="btn btn-primary mt-3">
            <i className="bi bi-arrow-left me-2"></i>Back to Tickets
          </Link>
        </div>
      </div>
    );
  }

  const isAssignedToCurrentUser =
    ticket.assignedEngineer && ticket.assignedEngineer.id === currentUser?.id;
  const isTicketCreator = ticket.requester && ticket.requester.id === currentUser?.id;

  return (
    <div className="container-fluid p-0">
      {/* Navigation Breadcrumbs */}
      <nav aria-label="breadcrumb" className="mb-3">
        <ol className="breadcrumb">
          <li className="breadcrumb-item">
            <Link to="/tickets" className="text-decoration-none">Tickets</Link>
          </li>
          <li className="breadcrumb-item active" aria-current="page">#{ticket.id}</li>
        </ol>
      </nav>

      {successMessage && (
        <div className="alert alert-success alert-dismissible fade show d-flex align-items-center mb-3" role="alert">
          <i className="bi bi-check-circle-fill me-2 fs-5"></i>
          <div>{successMessage}</div>
          <button
            type="button"
            className="btn-close"
            onClick={() => setSuccessMessage(null)}
          ></button>
        </div>
      )}

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Ticket Header & Action Buttons */}
      <div className="tc-card mb-4 p-4">
        <div className="d-flex flex-wrap justify-content-between align-items-start gap-3">
          <div>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <span className="fw-bold text-secondary fs-5">#{ticket.id}</span>
              <StatusBadge status={ticket.status} />
              <PriorityBadge priority={ticket.priority} />
              <span className="badge bg-light text-dark border">
                {formatEnum(ticket.category)}
              </span>
              {slaData && <SlaBadge status={slaData.overallStatus} />}
            </div>
            <h3 className="fw-bold text-slate-900 mb-1">{ticket.title}</h3>
            <p className="text-muted small mb-0">
              Submitted by <span className="fw-semibold text-slate-800">{ticket.requester?.name || 'Unknown'}</span>{' '}
              ({ticket.requester?.email}) on {formatDate(ticket.createdAt)}
            </p>
          </div>

          {/* Action Buttons */}
          <div className="d-flex flex-wrap gap-2">
            {/* Engineer Self-Assign Button */}
            {isEngineer && !ticket.assignedEngineer && ticket.status === 'OPEN' && (
              <button
                type="button"
                className="btn btn-outline-success d-flex align-items-center gap-2"
                onClick={handleSelfAssign}
                disabled={selfAssignSubmitting}
                aria-label="Self-assign this ticket"
              >
                {selfAssignSubmitting ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                    <span>Assigning...</span>
                  </>
                ) : (
                  <>
                    <i className="bi bi-person-plus"></i>
                    <span>Self Assign</span>
                  </>
                )}
              </button>
            )}

            {/* Manager / Admin Assign Button */}
            {(isManager || isAdmin) && (
              <button
                type="button"
                className="btn btn-outline-primary d-flex align-items-center gap-2"
                onClick={() => setShowAssignModal(true)}
                aria-label="Assign or reassign ticket"
              >
                <i className="bi bi-person-gear"></i>
                <span>{ticket.assignedEngineer ? 'Reassign' : 'Assign'}</span>
              </button>
            )}

            {/* Ask AI about this ticket Button (Phase 12 RAG Copilot) */}
            <Link
              to={`/ai-support?ticketId=${ticket.id}`}
              className="btn btn-outline-info d-flex align-items-center gap-2"
              title="Consult AI Support Copilot with this ticket's context"
              aria-label="Ask AI about this ticket"
            >
              <i className="bi bi-robot"></i>
              <span>Ask AI about this ticket</span>
            </Link>

            {/* Status Change Button */}
            {availableTransitions.length > 0 && (
              <button
                type="button"
                className="btn btn-primary d-flex align-items-center gap-2"
                onClick={() => {
                  setStatusForm({ status: '', reason: '', resolutionDescription: '' });
                  setShowStatusModal(true);
                }}
                aria-label="Update ticket status"
              >
                <i className="bi bi-arrow-left-right"></i>
                <span>Update Status</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Grid: Ticket Details & SLA Metrics */}
      <div className="row g-4 mb-4">
        {/* Left Column: Overview Details & Tabbed Sections */}
        <div className="col-12 col-lg-8">
          <div className="tc-card mb-4">
            <div className="tc-card-header">
              <ul className="nav nav-tabs card-header-tabs border-0">
                <li className="nav-item">
                  <button
                    className={`nav-link ${activeTab === 'overview' ? 'active' : ''}`}
                    onClick={() => setActiveTab('overview')}
                  >
                    <i className="bi bi-info-circle me-1"></i>Overview
                  </button>
                </li>
                <li className="nav-item">
                  <button
                    className={`nav-link ${activeTab === 'comments' ? 'active' : ''}`}
                    onClick={() => setActiveTab('comments')}
                  >
                    <i className="bi bi-chat-left-text me-1"></i>Comments ({comments.length})
                  </button>
                </li>
                <li className="nav-item">
                  <button
                    className={`nav-link ${activeTab === 'history' ? 'active' : ''}`}
                    onClick={() => setActiveTab('history')}
                  >
                    <i className="bi bi-clock-history me-1"></i>Status History ({history.length})
                  </button>
                </li>
                <li className="nav-item">
                  <button
                    className={`nav-link ${activeTab === 'assignments' ? 'active' : ''}`}
                    onClick={() => setActiveTab('assignments')}
                  >
                    <i className="bi bi-person-lines-fill me-1"></i>Assignments ({assignments.length})
                  </button>
                </li>
              </ul>
            </div>

            <div className="tc-card-body p-4">
              {/* Tab: Overview */}
              {activeTab === 'overview' && (
                <div>
                  <h6 className="fw-semibold text-slate-800 text-uppercase small mb-2">Description</h6>
                  <div
                    className="p-3 bg-light rounded border mb-4 text-slate-800"
                    style={{ whiteSpace: 'pre-wrap', lineHeight: '1.6' }}
                  >
                    {ticket.description}
                  </div>

                  {ticket.resolutionDescription && (
                    <div className="mb-4">
                      <h6 className="fw-semibold text-success text-uppercase small mb-2">
                        <i className="bi bi-check-circle-fill me-1"></i>Resolution Summary
                      </h6>
                      <div className="p-3 bg-success bg-opacity-10 border border-success-subtle rounded text-slate-800">
                        {ticket.resolutionDescription}
                        <div className="small text-muted mt-2">
                          Resolved at: {formatDate(ticket.resolvedAt)}
                        </div>
                      </div>
                    </div>
                  )}

                  <div className="row g-3 pt-2">
                    <div className="col-12 col-sm-6">
                      <div className="small text-muted">Department</div>
                      <div className="fw-semibold">{ticket.departmentName || 'IT Support'}</div>
                    </div>
                    <div className="col-12 col-sm-6">
                      <div className="small text-muted">Assigned Team</div>
                      <div className="fw-semibold">{ticket.teamName || 'Unassigned Team'}</div>
                    </div>
                    <div className="col-12 col-sm-6">
                      <div className="small text-muted">Last Updated</div>
                      <div className="fw-semibold">{formatDate(ticket.updatedAt)}</div>
                    </div>
                    <div className="col-12 col-sm-6">
                      <div className="small text-muted">First Response Timestamp</div>
                      <div className="fw-semibold">
                        {ticket.respondedAt ? formatDate(ticket.respondedAt) : 'Awaiting Response'}
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Tab: Comments */}
              {activeTab === 'comments' && (
                <div>
                  {/* Add Comment Form */}
                  <form onSubmit={handleAddComment} className="mb-4">
                    <div className="mb-2">
                      <label htmlFor="newCommentContent" className="form-label small fw-semibold text-slate-700">
                        Add a Comment
                      </label>
                      <textarea
                        id="newCommentContent"
                        className="form-control"
                        rows="3"
                        placeholder="Write a clear, helpful update..."
                        value={newComment}
                        onChange={(e) => setNewComment(e.target.value)}
                        required
                      ></textarea>
                    </div>

                    <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                      {isStaff ? (
                        <div className="form-check">
                          <input
                            type="checkbox"
                            className="form-check-input"
                            id="internalCommentCheck"
                            checked={isInternalComment}
                            onChange={(e) => setIsInternalComment(e.target.checked)}
                          />
                          <label className="form-check-label small text-warning-emphasis fw-medium" htmlFor="internalCommentCheck">
                            <i className="bi bi-shield-lock me-1"></i>Internal staff note (hidden from employee)
                          </label>
                        </div>
                      ) : <div></div>}

                      <button
                        type="submit"
                        className="btn btn-primary btn-sm px-3"
                        disabled={commentSubmitting || !newComment.trim()}
                      >
                        {commentSubmitting ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-1" role="status"></span>
                            Posting...
                          </>
                        ) : (
                          <>
                            <i className="bi bi-chat-right-text me-1"></i>Post Comment
                          </>
                        )}
                      </button>
                    </div>
                  </form>

                  {/* Comment List */}
                  {comments.length === 0 ? (
                    <div className="text-center py-4 text-muted small">
                      <i className="bi bi-chat-square-dots fs-3 d-block mb-1"></i>
                      No comments have been recorded on this ticket yet.
                    </div>
                  ) : (
                    <div>
                      {comments.map((c) => (
                        <div
                          key={c.id}
                          className={`tc-comment-card ${c.isInternal ? 'is-internal' : ''}`}
                        >
                          <div className="d-flex justify-content-between align-items-center mb-2">
                            <div className="d-flex align-items-center gap-2">
                              <span className="fw-semibold text-slate-800">{c.authorName}</span>
                              <span className="badge bg-light text-secondary border" style={{ fontSize: '0.7rem' }}>
                                {formatRole(c.authorRole)}
                              </span>
                              {c.isInternal && (
                                <span className="badge bg-warning text-dark border border-warning-subtle" style={{ fontSize: '0.68rem' }}>
                                  <i className="bi bi-lock-fill me-1"></i>Internal Note
                                </span>
                              )}
                            </div>
                            <span className="text-muted small" style={{ fontSize: '0.75rem' }}>
                              {formatDate(c.createdAt)}
                            </span>
                          </div>
                          <div className="text-slate-800" style={{ whiteSpace: 'pre-wrap', fontSize: '0.9rem' }}>
                            {c.content}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Tab: Status History */}
              {activeTab === 'history' && (
                <div>
                  {history.length === 0 ? (
                    <div className="text-center py-4 text-muted small">
                      No status transitions recorded yet.
                    </div>
                  ) : (
                    <div className="tc-timeline">
                      {history.map((h) => (
                        <div key={h.id} className="tc-timeline-item">
                          <div className="tc-timeline-indicator"></div>
                          <div className="tc-timeline-content">
                            <div className="d-flex justify-content-between align-items-center mb-1">
                              <div className="d-flex align-items-center gap-2">
                                <StatusBadge status={h.newStatus} />
                                {h.oldStatus && (
                                  <span className="text-muted small">
                                    from <StatusBadge status={h.oldStatus} />
                                  </span>
                                )}
                              </div>
                              <span className="small text-muted">{formatDate(h.changedAt)}</span>
                            </div>
                            <div className="small text-muted">
                              Changed by: <span className="fw-semibold text-slate-800">{h.changedByName}</span>
                            </div>
                            {h.changeReason && (
                              <div className="mt-1 small text-secondary fst-italic">
                                "{h.changeReason}"
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Tab: Assignments */}
              {activeTab === 'assignments' && (
                <div>
                  {assignments.length === 0 ? (
                    <div className="text-center py-4 text-muted small">
                      No assignments recorded yet.
                    </div>
                  ) : (
                    <div className="table-responsive">
                      <table className="table table-sm align-middle">
                        <thead className="table-light">
                          <tr>
                            <th>Engineer</th>
                            <th>Team</th>
                            <th>Assigned By</th>
                            <th>Assigned At</th>
                            <th>Notes</th>
                          </tr>
                        </thead>
                        <tbody>
                          {assignments.map((a) => (
                            <tr key={a.id}>
                              <td className="fw-semibold text-primary">
                                <i className="bi bi-person me-1"></i>
                                {a.assignedEngineerName}
                              </td>
                              <td>{a.assignedTeamName || '—'}</td>
                              <td>{a.assignedByName}</td>
                              <td className="small text-muted">{formatDate(a.assignedAt)}</td>
                              <td className="small text-muted">{a.notes || '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: SLA Dashboard & Assignment Overview Card */}
        <div className="col-12 col-lg-4">
          {/* SLA Performance Card */}
          <div className="tc-card mb-4">
            <div className="tc-card-header">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-clock-history text-primary"></i>
                <h6 className="mb-0 fw-semibold">SLA Status & Deadlines</h6>
              </div>
              {slaData && <SlaBadge status={slaData.overallStatus} />}
            </div>

            <div className="tc-card-body p-3">
              {slaData ? (
                <div>
                  {/* Paused Alert */}
                  {slaData.isPaused && (
                    <div className="alert alert-secondary py-2 px-3 small d-flex align-items-center mb-3">
                      <i className="bi bi-pause-circle-fill me-2 fs-5"></i>
                      <div>
                        <strong>SLA Timer Paused</strong>
                        <div style={{ fontSize: '0.75rem' }}>
                          Paused at {formatDate(slaData.slaPausedAt)} (Total paused:{' '}
                          {formatDurationMinutes(slaData.totalPausedDurationMinutes)})
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Response SLA */}
                  <div className="p-3 bg-light rounded border mb-3">
                    <div className="d-flex justify-content-between align-items-center mb-1">
                      <span className="small fw-semibold text-muted text-uppercase">First Response</span>
                      <SlaBadge status={slaData.responseStatus} />
                    </div>
                    <div className="small mb-1">
                      <strong>Deadline:</strong> {formatDate(slaData.responseDeadline)}
                    </div>
                    <div className="small">
                      <strong>Remaining:</strong>{' '}
                      <span
                        className={
                          slaData.responseStatus === 'BREACHED'
                            ? 'text-danger fw-bold'
                            : slaData.responseStatus === 'AT_RISK'
                            ? 'text-warning fw-bold'
                            : 'text-success'
                        }
                      >
                        {formatDurationMinutes(slaData.remainingResponseMinutes)}
                      </span>
                    </div>
                  </div>

                  {/* Resolution SLA */}
                  <div className="p-3 bg-light rounded border">
                    <div className="d-flex justify-content-between align-items-center mb-1">
                      <span className="small fw-semibold text-muted text-uppercase">Full Resolution</span>
                      <SlaBadge status={slaData.resolutionStatus} />
                    </div>
                    <div className="small mb-1">
                      <strong>Deadline:</strong> {formatDate(slaData.resolutionDeadline)}
                    </div>
                    <div className="small">
                      <strong>Remaining:</strong>{' '}
                      <span
                        className={
                          slaData.resolutionStatus === 'BREACHED'
                            ? 'text-danger fw-bold'
                            : slaData.resolutionStatus === 'AT_RISK'
                            ? 'text-warning fw-bold'
                            : 'text-success'
                        }
                      >
                        {formatDurationMinutes(slaData.remainingResolutionMinutes)}
                      </span>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="text-muted small mb-0">SLA metrics are being computed for this priority.</p>
              )}
            </div>
          </div>

          {/* Ticket Personnel Card */}
          <div className="tc-card">
            <div className="tc-card-header">
              <h6 className="mb-0 fw-semibold">Support Ownership</h6>
            </div>
            <div className="tc-card-body p-3">
              <div className="mb-3">
                <span className="text-muted small d-block">Assigned Engineer</span>
                {ticket.assignedEngineer ? (
                  <div className="d-flex align-items-center gap-2 mt-1">
                    <div className="rounded-circle bg-primary bg-opacity-10 text-primary p-2">
                      <i className="bi bi-person-badge"></i>
                    </div>
                    <div>
                      <div className="fw-semibold text-slate-900">{ticket.assignedEngineer.name}</div>
                      <div className="small text-muted">{ticket.assignedEngineer.email}</div>
                    </div>
                  </div>
                ) : (
                  <div className="badge bg-light text-muted border p-2 mt-1 w-100 text-start">
                    <i className="bi bi-dash-circle me-1"></i>Unassigned (In Open Queue)
                  </div>
                )}
              </div>

              <div>
                <span className="text-muted small d-block">Requester Information</span>
                <div className="d-flex align-items-center gap-2 mt-1">
                  <div className="rounded-circle bg-secondary bg-opacity-10 text-secondary p-2">
                    <i className="bi bi-person"></i>
                  </div>
                  <div>
                    <div className="fw-semibold text-slate-900">{ticket.requester?.name || '—'}</div>
                    <div className="small text-muted">{ticket.requester?.email || '—'}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Status Transition Modal */}
      {showStatusModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title fw-bold">Update Ticket Status</h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowStatusModal(false)}
                ></button>
              </div>
              <form onSubmit={handleUpdateStatus}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label htmlFor="statusSelect" className="form-label small fw-semibold text-slate-700">
                      New Status <span className="text-danger">*</span>
                    </label>
                    <select
                      id="statusSelect"
                      className="form-select"
                      value={statusForm.status}
                      onChange={(e) =>
                        setStatusForm((prev) => ({ ...prev, status: e.target.value }))
                      }
                      required
                    >
                      <option value="">Select Next Status...</option>
                      {availableTransitions.map((t) => (
                        <option key={t.value} value={t.value}>
                          {t.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  {statusForm.status === 'RESOLVED' && (
                    <div className="mb-3">
                      <label htmlFor="resolutionDescriptionInput" className="form-label small fw-semibold text-slate-700">
                        Resolution Description <span className="text-danger">*</span>
                      </label>
                      <textarea
                        id="resolutionDescriptionInput"
                        className="form-control"
                        rows="3"
                        placeholder="Detail how the issue was diagnosed and resolved..."
                        value={statusForm.resolutionDescription}
                        onChange={(e) =>
                          setStatusForm((prev) => ({
                            ...prev,
                            resolutionDescription: e.target.value,
                          }))
                        }
                        required
                      ></textarea>
                    </div>
                  )}

                  <div className="mb-3">
                    <label htmlFor="statusReasonInput" className="form-label small fw-semibold text-slate-700">
                      Change Reason / Remarks
                    </label>
                    <input
                      id="statusReasonInput"
                      type="text"
                      className="form-control"
                      placeholder="e.g., Investigation completed, patch deployed."
                      value={statusForm.reason}
                      onChange={(e) =>
                        setStatusForm((prev) => ({ ...prev, reason: e.target.value }))
                      }
                    />
                  </div>
                </div>

                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setShowStatusModal(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={statusSubmitting || !statusForm.status}
                  >
                    {statusSubmitting ? 'Saving...' : 'Confirm Status Change'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Assignment Modal (Manager / Admin) */}
      {showAssignModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title fw-bold">Assign Ticket</h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowAssignModal(false)}
                  aria-label="Close"
                ></button>
              </div>
              <form onSubmit={handleAssignSubmit}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label htmlFor="engineerIdInput" className="form-label small fw-semibold text-slate-700">
                      Engineer ID <span className="text-danger">*</span>
                    </label>
                    <input
                      id="engineerIdInput"
                      type="number"
                      className="form-control"
                      placeholder="e.g. 2 (Engineer Alex)"
                      value={assignForm.engineerId}
                      onChange={(e) =>
                        setAssignForm((prev) => ({ ...prev, engineerId: e.target.value }))
                      }
                      required
                    />
                    <div className="form-text small">
                      Enter the numerical User ID of the IT Support Engineer.
                    </div>
                  </div>

                  <div className="mb-3">
                    <label htmlFor="teamIdInput" className="form-label small fw-semibold text-slate-700">
                      Team ID (Optional)
                    </label>
                    <input
                      id="teamIdInput"
                      type="number"
                      className="form-control"
                      placeholder="e.g. 1 (Network Support)"
                      value={assignForm.teamId}
                      onChange={(e) =>
                        setAssignForm((prev) => ({ ...prev, teamId: e.target.value }))
                      }
                    />
                  </div>

                  <div className="mb-3">
                    <label htmlFor="assignmentNotesInput" className="form-label small fw-semibold text-slate-700">
                      Assignment Notes
                    </label>
                    <input
                      id="assignmentNotesInput"
                      type="text"
                      className="form-control"
                      placeholder="e.g. Assigned per network triage rotation."
                      value={assignForm.notes}
                      onChange={(e) =>
                        setAssignForm((prev) => ({ ...prev, notes: e.target.value }))
                      }
                    />
                  </div>
                </div>

                <div className="modal-footer">
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setShowAssignModal(false)}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={assignSubmitting || !assignForm.engineerId}
                  >
                    {assignSubmitting ? 'Assigning...' : 'Confirm Assignment'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TicketDetails;
