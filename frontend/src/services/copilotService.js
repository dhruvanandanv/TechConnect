import api from './api';

export const copilotService = {
  /**
   * Dispatches support inquiry to the RAG AI Support Copilot backend.
   * Spring Boot handles auth & ticket context validation, retrieves knowledge chunks,
   * calls Python RAG service, and returns grounded answer with citations.
   */
  async getAnswer({ query, category, ticketId, topK = 5, minSimilarity = 0.30 } = {}) {
    const payload = {
      query: query || '',
      topK,
      minSimilarity,
    };

    if (category && category !== 'ALL') {
      payload.category = category;
    }

    if (ticketId) {
      payload.ticketId = Number(ticketId);
    }

    const response = await api.post('/ai/copilot/answer', payload);
    return response.data;
  },
};

export default copilotService;
