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
import { UsersPage } from '@/pages/UsersPage';
import { useAuth } from '@/context/AuthContext';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
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
        <Route path="archive" element={<IncidentsPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="agencies" element={<AgenciesPage />} />
        <Route path="responders" element={<RespondersPage />} />
      </Route>
    </Routes>
  );
}

export default App;
