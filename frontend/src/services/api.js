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

// Request Interceptor: Attach JWT Bearer token if stored
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('techconnect_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
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
        // Clear stale session
        localStorage.removeItem('techconnect_token');
        localStorage.removeItem('techconnect_user');
        
        // Notify application components of auth expiry
        window.dispatchEvent(new CustomEvent('techconnect:auth-expired', {
          detail: { message: data?.message || 'Session expired. Please log in again.' }
        }));
      }
    }

    return Promise.reject(error);
  }
);

/**
 * Extracts a user-friendly error message from backend ErrorResponse or Axios error.
 */
export const extractErrorMessage = (error) => {
  if (!error) return 'An unexpected error occurred.';
  if (typeof error === 'string') return error;

  if (error.response?.data) {
    const data = error.response.data;
    if (data.errors && Array.isArray(data.errors) && data.errors.length > 0) {
      return data.errors.join(' | ');
    }
    if (data.message) {
      return data.message;
    }
  }

  if (error.message) {
    if (error.code === 'ERR_NETWORK') {
      return 'Unable to connect to the TechConnect server. Please ensure the backend is running.';
    }
    return error.message;
  }

  return 'An unexpected error occurred. Please try again.';
};

export default api;
