import { Routes, Route } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { IncidentsPage } from '@/pages/IncidentsPage';
import { AgenciesPage } from '@/pages/AgenciesPage';
import { RespondersPage } from '@/pages/RespondersPage';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="agencies" element={<AgenciesPage />} />
        <Route path="responders" element={<RespondersPage />} />
      </Route>
    </Routes>
  );
}

export default App;
