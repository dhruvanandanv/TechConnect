import api from './api';

export const slaService = {
  /**
   * Retrieves SLA deadline calculations, remaining time, and pause state for a ticket.
   */
  async getTicketSla(ticketId) {
    const response = await api.get(`/tickets/${ticketId}/sla`);
    return response.data;
  },

  /**
   * Retrieves all tickets currently breached across the organization (Manager / Admin only).
   */
  async getBreachedTickets() {
    const response = await api.get('/sla/breached');
    return response.data;
  },

  /**
   * Retrieves platform-wide SLA summary metrics (Manager / Admin only).
   */
  async getSlaSummary() {
    const response = await api.get('/sla/summary');
    return response.data;
  },
};

export default slaService;
