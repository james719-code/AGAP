import { Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from '@/pages/LandingPage';
import { LoginPage } from '@/pages/LoginPage';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { MapPage } from '@/pages/MapPage';
import { ChatPage } from '@/pages/ChatPage';
import { InsightsPage } from '@/pages/InsightsPage';
import { IncidentsPage } from '@/pages/IncidentsPage';
import { AgenciesPage } from '@/pages/AgenciesPage';
import { RespondersPage } from '@/pages/RespondersPage';
import { MessageCatalogPage } from '@/pages/MessageCatalogPage';
import { EmergencyTypesPage } from '@/pages/EmergencyTypesPage';
import { ReportsPage } from '@/pages/ReportsPage';
import { AuditLogsPage } from '@/pages/AuditLogsPage';
import { useAuth } from '@/context/AuthContext';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-400 text-sm">
        Loading...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
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
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="map" element={<MapPage />} />
        <Route path="chat" element={<ChatPage />} />
        <Route path="insights" element={<InsightsPage />} />
        <Route path="team" element={<RespondersPage />} />
        <Route path="archive" element={<IncidentsPage variant="archive" />} />
        <Route path="incidents" element={<IncidentsPage variant="all" />} />
        <Route path="agencies" element={<AgenciesPage />} />
        <Route path="emergency-types" element={<EmergencyTypesPage />} />
        <Route path="messages" element={<MessageCatalogPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="audit-logs" element={<AuditLogsPage />} />
      </Route>
    </Routes>
  );
}

export default App;