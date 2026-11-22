import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '@/context/ThemeContext';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { MapPage } from '@/pages/MapPage';
import { IncidentsPage } from '@/pages/IncidentsPage';
import { ChatPage } from '@/pages/ChatPage';
import { RespondersPage } from '@/pages/RespondersPage';
import { UsersPage } from '@/pages/UsersPage';
import { AgenciesPage } from '@/pages/AgenciesPage';
import { EmergencyTypesPage } from '@/pages/EmergencyTypesPage';
import { MessageCatalogPage } from '@/pages/MessageCatalogPage';
import { TelemetryReportsPage } from '@/pages/TelemetryReportsPage';
import { AuditLogsPage } from '@/pages/AuditLogsPage';
import './preview.css';

createRoot(document.getElementById('root')!).render(
  <BrowserRouter basename={import.meta.env.BASE_URL}>
    <ThemeProvider>
      <div className="fixed bottom-3 right-3 z-[1000] pointer-events-none rounded-full border border-amber-400/50 bg-amber-100 px-3 py-1.5 text-xs font-bold text-amber-950 shadow-lg" role="status">Synthetic data · Read-only UI preview</div>
      <Routes>
        <Route path="/dashboard" element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="map" element={<MapPage />} />
          <Route path="incidents" element={<IncidentsPage key="all" variant="all" />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="team" element={<RespondersPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="agencies" element={<AgenciesPage />} />
          <Route path="emergency-types" element={<EmergencyTypesPage />} />
          <Route path="messages" element={<MessageCatalogPage />} />
          <Route path="telemetry-reports" element={<TelemetryReportsPage />} />
          <Route path="audit-logs" element={<AuditLogsPage />} />
          <Route path="archive" element={<IncidentsPage key="archive" variant="archive" />} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </ThemeProvider>
  </BrowserRouter>,
);
