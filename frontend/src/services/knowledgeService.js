import api from './api';

export const knowledgeService = {
  /**
   * Retrieves paginated articles with optional filters.
   */
  async getArticles({ category, status, tag, page = 0, size = 10, sort = 'updated_at,desc' } = {}) {
    const params = { page, size, sort };
    if (category && category !== 'ALL') params.category = category;
    if (status) params.status = status;
    if (tag) params.tag = tag;

    const response = await api.get('/knowledge/articles', { params });
    return response.data;
  },

  /**
   * Retrieves a single article by its MongoDB ID.
   */
  async getArticleById(id) {
    const response = await api.get(`/knowledge/articles/${id}`);
    return response.data;
  },

  /**
   * Retrieves a single article by its unique URL slug.
   */
  async getArticleBySlug(slug) {
    const response = await api.get(`/knowledge/articles/slug/${slug}`);
    return response.data;
  },

  /**
   * Keyword search for articles across title, summary, problem, cause, resolution, tags.
   */
  async searchArticles({ q, page = 0, size = 10 } = {}) {
    const params = { q: q || '', page, size };
    const response = await api.get('/knowledge/search', { params });
    return response.data;
  },

  /**
   * Creates a new knowledge article (Engineer, Manager, Admin).
   */
  async createArticle(data) {
    const response = await api.post('/knowledge/articles', data);
    return response.data;
  },

  /**
   * Updates an existing knowledge article.
   */
  async updateArticle(id, data) {
    const response = await api.put(`/knowledge/articles/${id}`, data);
    return response.data;
  },

  /**
   * Transitions article status to PUBLISHED.
   */
  async publishArticle(id) {
    const response = await api.patch(`/knowledge/articles/${id}/publish`);
    return response.data;
  },

  /**
   * Transitions article status to ARCHIVED.
   */
  async archiveArticle(id) {
    const response = await api.patch(`/knowledge/articles/${id}/archive`);
    return response.data;
  },

  /**
   * Reverts or restores article to DRAFT status.
   */
  async revertToDraft(id) {
    const response = await api.patch(`/knowledge/articles/${id}/draft`);
    return response.data;
  },

  /**
   * Submits helpful/not helpful feedback for a published article.
   */
  async submitFeedback(id, { helpful }) {
    const response = await api.post(`/knowledge/articles/${id}/feedback`, { helpful });
    return response.data;
  },

  /**
   * Retrieves audit and version history for an article.
   */
  async getArticleHistory(id) {
    const response = await api.get(`/knowledge/articles/${id}/history`);
    return response.data;
  },

  /**
   * Retrieves all available IT knowledge categories.
   */
  async getCategories() {
    const response = await api.get('/knowledge/categories');
    return response.data;
  },

  /**
   * Retrieves distinct tags across published articles.
   */
  async getTags() {
    const response = await api.get('/knowledge/tags');
    return response.data;
  },

  /**
   * Executes dense vector semantic search across knowledge article chunks.
   */
  async semanticSearch({ q, category, topK = 5, minSimilarity = 0.40 } = {}) {
    const payload = {
      query: q || '',
      topK,
      minSimilarity,
    };
    if (category && category !== 'ALL') {
      payload.category = category;
    }
    const response = await api.post('/knowledge/semantic-search', payload);
    return response.data;
  },

  /**
   * Triggers batch ingestion and embedding for pending articles (Staff only).
   */
  async runIngestion() {
    const response = await api.post('/knowledge/ingestion/run');
    return response.data;
  },

  /**
   * Reindexes a single article version into the semantic vector database.
   */
  async reindexArticle(id) {
    const response = await api.post(`/knowledge/articles/${id}/reindex`);
    return response.data;
  },
};

export default knowledgeService;
