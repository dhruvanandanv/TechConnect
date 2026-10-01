import api from './api';

const TOKEN_KEY = 'techconnect_token';
const USER_KEY = 'techconnect_user';

export const authService = {
  /**
   * Submits user credentials and receives JWT authentication token and user profile.
   */
  async login(email, password) {
    const response = await api.post('/auth/login', { email, password });
    const data = response.data;
    if (data.token) {
      localStorage.setItem(TOKEN_KEY, data.token);
      if (data.user) {
        localStorage.setItem(USER_KEY, JSON.stringify(data.user));
      }
    }
    return data;
  },

  /**
   * Registers a new employee account.
   */
  async register({ name, email, password }) {
    const response = await api.post('/auth/register', { name, email, password });
    return response.data;
  },

  /**
   * Clears stored token and user details.
   */
  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  /**
   * Retrieves the currently stored token.
   */
  getToken() {
    return localStorage.getItem(TOKEN_KEY);
  },

  /**
   * Retrieves the currently stored user profile from localStorage.
   */
  getCurrentUser() {
    const userStr = localStorage.getItem(USER_KEY);
    if (!userStr) return null;
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  },

  /**
   * Checks if user has a stored JWT.
   */
  isAuthenticated() {
    return !!localStorage.getItem(TOKEN_KEY);
  },
};

export default authService;
