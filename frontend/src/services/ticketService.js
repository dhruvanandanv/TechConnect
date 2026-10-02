import api from './api';

export const ticketService = {
  /**
   * Retrieves role-scoped tickets with optional filtering, sorting, and pagination.
   * Scoping:
   * - ROLE_EMPLOYEE: Tickets submitted by the user
   * - ROLE_ENGINEER: Tickets assigned to engineer, created by engineer, or unassigned OPEN
   * - ROLE_MANAGER: Tickets belonging to manager's team or department
   * - ROLE_ADMIN: System-wide tickets
   */
  async getMyTickets({ status, priority, category, page = 0, size = 10, sort = 'createdAt,desc' } = {}) {
    const params = { page, size, sort };
    if (status) params.status = status;
    if (priority) params.priority = priority;
    if (category) params.category = category;

    const response = await api.get('/tickets/my', { params });
    return response.data;
  },

  /**
   * Retrieves full details for a single ticket by ID.
   */
  async getTicketById(id) {
    const response = await api.get(`/tickets/${id}`);
    return response.data;
  },

  /**
   * Requests automated AI ticket intelligence analysis (category, priority, team, summary, reasons).
   */
  async analyzeTicket({ title, description, category, priority }) {
    const response = await api.post('/tickets/analyze', {
      title,
      description,
      category,
      priority,
    });
    return response.data;
  },

  /**
   * Submits a new IT service ticket.
   */
  async createTicket(ticketData) {
    const response = await api.post('/tickets', ticketData);
    return response.data;
  },

  /**
   * Updates permitted ticket fields (title, description, category, priority).
   */
  async updateTicket(id, ticketData) {
    const response = await api.patch(`/tickets/${id}`, ticketData);
    return response.data;
  },

  /**
   * Transitions ticket status (e.g. IN_PROGRESS, WAITING_FOR_USER, RESOLVED, CLOSED, ESCALATED, MANAGER_REVIEW).
   */
  async updateTicketStatus(id, { status, reason, resolutionDescription }) {
    const response = await api.patch(`/tickets/${id}/status`, {
      status,
      reason,
      resolutionDescription,
    });
    return response.data;
  },

  /**
   * Assigns or reassigns ticket to an engineer / team.
   */
  async assignTicket(id, { engineerId, teamId, notes }) {
    const response = await api.patch(`/tickets/${id}/assignment`, {
      engineerId,
      teamId,
      notes,
    });
    return response.data;
  },

  /**
   * Adds a new comment to a ticket.
   */
  async addComment(id, { content, isInternal = false }) {
    const response = await api.post(`/tickets/${id}/comments`, {
      content,
      isInternal,
    });
    return response.data;
  },

  /**
   * Retrieves all comments for a ticket.
   */
  async getComments(id) {
    const response = await api.get(`/tickets/${id}/comments`);
    return response.data;
  },

  /**
   * Retrieves status history transitions for a ticket.
   */
  async getStatusHistory(id) {
    const response = await api.get(`/tickets/${id}/history`);
    return response.data;
  },

  /**
   * Retrieves assignment audit log for a ticket.
   */
  async getAssignmentHistory(id) {
    const response = await api.get(`/tickets/${id}/assignments`);
    return response.data;
  },
};

export default ticketService;
