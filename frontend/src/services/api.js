import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  timeout: 15000,
});

// Request Interceptor: Attach JWT Bearer token only if valid token is stored
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('techconnect_token');
    if (token && typeof token === 'string' && token.trim() && token !== 'null' && token !== 'undefined') {
      config.headers.Authorization = `Bearer ${token.trim()}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Centralized Error and 401 Session Expiry Handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response;

      if (status === 401) {
        const url = error.config?.url || '';
        const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register');

        // Only broadcast session expiry for authenticated routes, not credential failures on login
        if (!isAuthEndpoint) {
          localStorage.removeItem('techconnect_token');
          localStorage.removeItem('techconnect_user');

          window.dispatchEvent(
            new CustomEvent('techconnect:auth-expired', {
              detail: { message: data?.message || 'Your session has expired. Please log in again.' },
            })
          );
        }
      }
    }

    return Promise.reject(error);
  }
);

/**
 * Extracts a user-friendly error message from backend ErrorResponse, HTTP status, or network failure.
 */
export const extractErrorMessage = (error) => {
  if (!error) return 'An unexpected error occurred.';
  if (typeof error === 'string') return error;

  // 1. Network connectivity / server unavailable errors
  if (!error.response) {
    if (error.code === 'ECONNABORTED' || error.message?.toLowerCase().includes('timeout')) {
      return 'Request timed out. Unable to connect to TechConnect server.';
    }
    if (
      error.code === 'ERR_NETWORK' ||
      error.message === 'Network Error' ||
      error.request
    ) {
      return 'Unable to connect to TechConnect server. Please ensure the backend is running.';
    }
    return error.message || 'Unable to connect to TechConnect server.';
  }

  // 2. Structured backend ErrorResponse
  const { status, data } = error.response;
  if (data) {
    if (data.errors && Array.isArray(data.errors) && data.errors.length > 0) {
      return data.errors.join(' | ');
    }
    if (data.message && typeof data.message === 'string') {
      return data.message;
    }
  }

  // 3. Status code standard fallbacks
  switch (status) {
    case 400:
      return 'Invalid request submitted. Please check your input.';
    case 401:
      return 'Your session has expired. Please log in again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'The requested operation conflicts with the current ticket state.';
    case 500:
    case 502:
    case 503:
      return 'Something went wrong on the server. Please try again later.';
    default:
      return error.message || 'An unexpected error occurred. Please try again.';
  }
};

export default api;
