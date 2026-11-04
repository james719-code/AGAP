import React, { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ShieldAlert, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ThemeProvider } from '@/context/ThemeContext';

const AppShell = lazy(() => import('@/components/layout/AppShell').then(m => ({ default: m.AppShell })));
const LandingPage = lazy(() => import('@/pages/LandingPage').then(m => ({ default: m.LandingPage })));
const LoginPage = lazy(() => import('@/pages/LoginPage').then(m => ({ default: m.LoginPage })));
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const MapPage = lazy(() => import('@/pages/MapPage').then(m => ({ default: m.MapPage })));
const ChatPage = lazy(() => import('@/pages/ChatPage').then(m => ({ default: m.ChatPage })));
const IncidentsPage = lazy(() => import('@/pages/IncidentsPage').then(m => ({ default: m.IncidentsPage })));
const AgenciesPage = lazy(() => import('@/pages/AgenciesPage').then(m => ({ default: m.AgenciesPage })));
const RespondersPage = lazy(() => import('@/pages/RespondersPage').then(m => ({ default: m.RespondersPage })));
const MessageCatalogPage = lazy(() => import('@/pages/MessageCatalogPage').then(m => ({ default: m.MessageCatalogPage })));
const EmergencyTypesPage = lazy(() => import('@/pages/EmergencyTypesPage').then(m => ({ default: m.EmergencyTypesPage })));
const TelemetryReportsPage = lazy(() => import('@/pages/TelemetryReportsPage').then(m => ({ default: m.TelemetryReportsPage })));
const AuditLogsPage = lazy(() => import('@/pages/AuditLogsPage').then(m => ({ default: m.AuditLogsPage })));
const UsersPage = lazy(() => import('@/pages/UsersPage').then(m => ({ default: m.UsersPage })));

function PageLoader() {
  return (
    <div className="flex h-[50vh] w-full flex-col items-center justify-center gap-3 text-slate-400">
      <div className="h-7 w-7 animate-spin rounded-full border-2 border-emerald-500 border-t-transparent" />
      <span className="text-xs font-medium tracking-wider uppercase text-slate-400">Loading module...</span>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-400 text-sm">
        Loading AGAP Command Console...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user && user.role !== 'ADMIN') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-slate-950 text-slate-100 p-6 text-center font-sans">
        <div className="p-4 rounded-full bg-red-500/10 border border-red-500/30 text-red-500 mb-4 animate-in zoom-in">
          <ShieldAlert className="h-10 w-10" />
        </div>
        <h1 className="text-2xl font-bold text-white tracking-tight">Access Denied — Admin Authorization Required</h1>
        <p className="text-sm text-slate-400 max-w-md mt-2 leading-relaxed">
          The AGAP Command Center Web Portal is restricted exclusively to authorized System Administrators. Your account (<strong>@{user.username}</strong>) is assigned role <strong>{user.role}</strong>.
        </p>
        <Button onClick={() => logout()} className="mt-6 gap-2 bg-red-600 hover:bg-red-700 text-white font-semibold text-xs">
          <LogOut className="h-4 w-4" /> Log Out &amp; Return to Login
        </Button>
      </div>
    );
  }

  return <>{children}</>;
}

export function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Public Landing Page */}
        <Route path="/" element={<LandingPage />} />

        {/* Admin Login Page */}
        <Route path="/login" element={<LoginPage />} />

        {/* Command Center Dashboard App (Protected) */}
        <Route
          path="/dashboard"
          element={
            <ThemeProvider>
              <ProtectedRoute>
                <AppShell />
              </ProtectedRoute>
            </ThemeProvider>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="map" element={<MapPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="telemetry-reports" element={<TelemetryReportsPage />} />
          <Route path="insights" element={<Navigate to="/dashboard/telemetry-reports" replace />} />
          <Route path="team" element={<RespondersPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="archive" element={<IncidentsPage variant="archive" />} />
          <Route path="incidents" element={<IncidentsPage variant="all" />} />
          <Route path="agencies" element={<AgenciesPage />} />
          <Route path="emergency-types" element={<EmergencyTypesPage />} />
          <Route path="messages" element={<MessageCatalogPage />} />
          <Route path="reports" element={<Navigate to="/dashboard/telemetry-reports" replace />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

export default App;

