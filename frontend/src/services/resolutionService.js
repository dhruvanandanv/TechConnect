import api from './api';

export const resolutionService = {
  /**
   * Requests an AI-generated, grounded resolution suggestion for an IT ticket.
   * Only accessible by authorized Support Engineers, Managers, and Admins.
   *
   * @param {number|string} ticketId - ID of the ticket
   * @param {Object} options - Optional retrieval parameters
   * @param {number} [options.topK=5] - Maximum knowledge chunks / similar tickets to retrieve
   * @param {number} [options.minSimilarity=0.30] - Minimum cosine similarity threshold
   * @returns {Promise<Object>} Resolution suggestion response
   */
  async getResolutionSuggestion(ticketId, { topK = 5, minSimilarity = 0.30 } = {}) {
    if (!ticketId) {
      throw new Error('ticketId is required to generate a resolution suggestion');
    }

    const payload = {
      topK,
      minSimilarity,
    };

    const response = await api.post(`/ai/tickets/${ticketId}/resolution-suggestion`, payload);
    return response.data;
  },
};

export default resolutionService;
