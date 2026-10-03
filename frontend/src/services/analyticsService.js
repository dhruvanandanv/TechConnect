import api from './api';

const analyticsService = {
  /**
   * Fetches real-time aggregated ITSM analytics metrics.
   * @returns {Promise<Object>} Aggregated analytics overview
   */
  async getOverview() {
    const response = await api.get('/analytics/overview');
    return response.data;
  },
};

export default analyticsService;
