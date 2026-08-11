import { Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from '@/pages/LandingPage';
import { LoginPage } from '@/pages/LoginPage';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { MapPage } from '@/pages/MapPage';
import { ChatPage } from '@/pages/ChatPage';
import { IncidentsPage } from '@/pages/IncidentsPage';
import { AgenciesPage } from '@/pages/AgenciesPage';
import { RespondersPage } from '@/pages/RespondersPage';
import { MessageCatalogPage } from '@/pages/MessageCatalogPage';
import { EmergencyTypesPage } from '@/pages/EmergencyTypesPage';
import { TelemetryReportsPage } from '@/pages/TelemetryReportsPage';
import { AuditLogsPage } from '@/pages/AuditLogsPage';
import { UsersPage } from '@/pages/UsersPage';
import { useAuth } from '@/context/AuthContext';
import { ShieldAlert, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ThemeProvider } from '@/context/ThemeContext';

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
  );
}

export default App;
