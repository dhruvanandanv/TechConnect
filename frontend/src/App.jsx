import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import RoleGuard from './components/RoleGuard';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';

// Pages
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Tickets from './pages/Tickets';
import CreateTicket from './pages/CreateTicket';
import TicketDetails from './pages/TicketDetails';
import SlaDashboard from './pages/SlaDashboard';
import Profile from './pages/Profile';
import KnowledgeBase from './pages/KnowledgeBase';
import KnowledgeArticleView from './pages/KnowledgeArticleView';
import KnowledgeArticleEditor from './pages/KnowledgeArticleEditor';
import NotFound from './pages/NotFound';

import './App.css';

/**
 * Main Layout wrapper providing consistent enterprise Navbar & Sidebar for protected routes.
 */
const AppLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="app-container">
      <Navbar onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
      <div className="app-body">
        <Sidebar isOpen={sidebarOpen} onCloseMobile={() => setSidebarOpen(false)} />
        <main className="tc-main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

/**
 * Public route guard redirecting authenticated users to /dashboard.
 */
const PublicRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) return null;
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
};

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Authentication Routes */}
          <Route
            path="/login"
            element={
              <PublicRoute>
                <Login />
              </PublicRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicRoute>
                <Register />
              </PublicRoute>
            }
          />

          {/* Protected Application Layout */}
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/tickets" element={<Tickets />} />
            <Route path="/my-tickets" element={<Tickets />} />
            <Route path="/assigned-tickets" element={<Tickets filterAssignedOnly={true} />} />
            <Route path="/tickets/new" element={<CreateTicket />} />
            <Route path="/tickets/:id" element={<TicketDetails />} />
            {/* Knowledge Base Management Routes (Phase 10) */}
            <Route path="/knowledge" element={<KnowledgeBase />} />
            <Route path="/knowledge/search" element={<KnowledgeBase />} />
            <Route path="/knowledge/articles/:id" element={<KnowledgeArticleView />} />
            <Route
              path="/knowledge/articles/new"
              element={
                <RoleGuard allowedRoles={['ROLE_ENGINEER', 'ROLE_MANAGER', 'ROLE_ADMIN']}>
                  <KnowledgeArticleEditor />
                </RoleGuard>
              }
            />
            <Route
              path="/knowledge/articles/:id/edit"
              element={
                <RoleGuard allowedRoles={['ROLE_ENGINEER', 'ROLE_MANAGER', 'ROLE_ADMIN']}>
                  <KnowledgeArticleEditor />
                </RoleGuard>
              }
            />

            {/* Manager and Admin SLA Dashboard */}
            <Route
              path="/sla"
              element={
                <RoleGuard allowedRoles={['ROLE_MANAGER', 'ROLE_ADMIN']}>
                  <SlaDashboard />
                </RoleGuard>
              }
            />
          </Route>

          {/* 404 Fallback */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
