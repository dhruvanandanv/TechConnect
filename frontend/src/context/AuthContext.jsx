import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import authService from '../services/authService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState(null);

  // Restore authentication state from storage
  useEffect(() => {
    try {
      const storedToken = authService.getToken();
      const storedUser = authService.getCurrentUser();

      if (storedToken && storedUser) {
        setToken(storedToken);
        setCurrentUser(storedUser);
      }
    } catch (err) {
      console.error('Failed to restore authentication state:', err);
      authService.logout();
    } finally {
      setLoading(false);
    }

    // Handle token expiry broadcast from Axios interceptor
    const handleAuthExpired = (event) => {
      setToken(null);
      setCurrentUser(null);
      setSessionExpiredMessage(event.detail?.message || 'Your session has expired. Please log in again.');
    };

    window.addEventListener('techconnect:auth-expired', handleAuthExpired);
    return () => {
      window.removeEventListener('techconnect:auth-expired', handleAuthExpired);
    };
  }, []);

  const login = useCallback(async (email, password) => {
    setSessionExpiredMessage(null);
    const data = await authService.login(email, password);
    setToken(data.token);
    setCurrentUser(data.user);
    return data;
  }, []);

  const register = useCallback(async (userData) => {
    return await authService.register(userData);
  }, []);

  const logout = useCallback(() => {
    authService.logout();
    setToken(null);
    setCurrentUser(null);
    setSessionExpiredMessage(null);
  }, []);

  const clearSessionMessage = useCallback(() => {
    setSessionExpiredMessage(null);
  }, []);

  const value = {
    currentUser,
    token,
    isAuthenticated: !!token && !!currentUser,
    loading,
    login,
    register,
    logout,
    sessionExpiredMessage,
    clearSessionMessage,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
